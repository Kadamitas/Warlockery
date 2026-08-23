package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import com.kadamitas.warlockery.entity.StormSimianRules.Concern;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Nine bounded live F28 fixtures. Every one asserts through spawned, AI enabled, self ticking Storm
 * Simians: no fixture calls {@link StormSimianRuntime#tick} by hand, every assertion runs inside a
 * {@code runAfterDelay} callback registered from the top level of its own test so no callback ever
 * registers another one, and every created entity, block and weather change is restored in
 * {@code finally}.
 *
 * <p>Arena geometry: the framework seals the {@code warlockery:empty3x3x3} cell in a barrier shell and
 * the cell has no interior floor at relative y=0, so every entity in these fixtures stays inside
 * relative 0..2 at y=1 and any grip support is placed deliberately. Because the interior floor is
 * absent, a simian in an untouched cell has no supported grip at all, which is what makes the
 * canopy fixtures able to control exactly which candidate qualifies.</p>
 *
 * <p>Weather is global to the level and fixtures in one batch share the world clock, so the storm
 * fixture records the whole weather state on entry and restores it on close rather than assuming
 * any neighbour left it alone.</p>
 *
 * <p>These fixtures depend on the coordinator deferred ModGameTests and GameTestInstanceContractTest
 * wiring to register these nine functions against the isolated environment.</p>
 */
public final class StormSimianGameTests {

    private StormSimianGameTests() {
    }

    // ---------------------------------------------------------------- canopy

    /**
     * Geometry corrected on first execution. The sealed cell's interior is air from relative y=0
     * upward and the barrier shell floor sits at relative y=-1, so an untouched cell already offers
     * the whole floor as support and the first run legitimately gripped relative (0,0,0). The
     * arrangement here removes that ambiguity without touching the shell: the simian starts at
     * relative (1,2,1) and one persistent leaf block is placed at relative (1,0,1), which makes
     * relative (1,1,1) the only candidate in the envelope with anything at all beneath it, at
     * distance one, and every other near candidate is over open air.
     */
    public static void stormSimianCanopyRouteIsSupportedAndBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.placeState(new BlockPos(1, 0, 1),
                Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
            final StormSimianEntity simian = spawnSimian(fixture, new BlockPos(1, 2, 1));
            final BlockPos expectedGrip = helper.absolutePos(new BlockPos(1, 1, 1));

            helper.runAfterDelay(100L, () -> {
                try {
                    final StormSimianRuntime.Counters counters = simian.stormSimianCounters();
                    helper.assertTrue(counters.gripSearches() >= 1L,
                        "a live self ticking simian with no grip reaches the canopy concern");
                    helper.assertValueEqual(counters.emptyGripSearches(), 0L,
                        "the one genuinely supported candidate qualifies on every sweep");
                    helper.assertTrue(counters.gripCandidateVisits()
                            <= StormSimianRules.GRIP_CANDIDATE_CAP * counters.gripSearches(),
                        "the sixteen candidate cap binds; visits=" + counters.gripCandidateVisits()
                            + ", sweeps=" + counters.gripSearches());
                    helper.assertTrue(counters.gripBlockReads() >= 4L,
                        "reads are genuinely charged rather than counted as zero");
                    helper.assertTrue(counters.gripBlockReads()
                            <= StormSimianRules.GRIP_READ_CAP * counters.gripSearches(),
                        "the sixty four read cap binds even though most candidates are rejected;"
                            + " reads=" + counters.gripBlockReads()
                            + ", sweeps=" + counters.gripSearches());
                    helper.assertTrue(counters.routeRequests() >= 1L,
                        "a qualified destination is genuinely offered to the navigator");
                    helper.assertTrue(counters.routeRequests() <= 8L,
                        "one request per twenty ticks, not one per tick; requests="
                            + counters.routeRequests());
                    helper.assertValueEqual(counters.routeRequests(),
                        counters.gripsTaken() + counters.routeFailures(),
                        "every request has exactly one recorded outcome");
                    helper.assertValueEqual(counters.blockWrites(), 0L,
                        "a canopy sweep reads the world and never writes it");
                    helper.assertTrue(counters.gripsTaken() >= 1L,
                        "the qualified destination is genuinely routed to and taken");
                    helper.assertValueEqual(
                        simian.stormSimianState().grip().orElseThrow(), expectedGrip,
                        "the nearest position in the envelope with support beneath it is the one"
                            + " taken");
                    helper.assertTrue(simian.stormSimianState().gripHoldTicks() > 0,
                        "taking a grip and starting its hold is one indivisible move");
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 0, 1))
                            .is(net.minecraft.tags.BlockTags.LEAVES),
                        "the canopy the simian gripped is left exactly as it was found");
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 1)).isAir(),
                        "the grip position itself is never filled in");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    /**
     * The route ledger, the exhausted state and recurring defect six in one live sequence: an open
     * backoff window genuinely stops requests, an urgent interruption clears the inherited failure
     * run without clearing the window, and requests resume only once the window itself has run out.
     */
    public static void stormSimianBlockedRouteBacksOff(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final AtomicLong frozenAt = new AtomicLong(-1L);
        try {
            final StormSimianEntity simian = spawnSimian(fixture, new BlockPos(0, 1, 0));

            helper.runAfterDelay(20L, () -> {
                try {
                    // The grip is released as well as the ledger planted. Corrected on first
                    // execution: the simian had already taken a grip from the arena floor, so the
                    // canopy concern was not due, and "no request was made" would have been true
                    // for a reason that had nothing to do with the backoff window under test.
                    simian.setStormSimianState(simian.stormSimianState().withoutGrip().withRoute(
                        new StormSimianState.Route(StormSimianRules.ROUTE_PERIOD_TICKS,
                            StormSimianRules.ROUTE_FAILURES_BEFORE_BACKOFF, 120)
                    ));
                    frozenAt.set(simian.stormSimianCounters().routeRequests());
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(40L, () -> {
                try {
                    helper.assertValueEqual(simian.stormSimianCounters().routeRequests(),
                        frozenAt.get(),
                        "an open backoff window stops the live tick requesting anything at all");
                    helper.assertValueEqual(
                        simian.stormSimianState().route().consecutiveFailures(),
                        StormSimianRules.ROUTE_FAILURES_BEFORE_BACKOFF,
                        "the exhausted failure run is genuinely observed by the live tick");
                    simian.igniteForSeconds(4.0F);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(60L, () -> {
                try {
                    helper.assertValueEqual(simian.stormSimianTransient().lastConcern(),
                        Concern.HAZARD,
                        "an escapable hazard outranks every routine concern in the live arbiter");
                    simian.clearFire();
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(90L, () -> {
                try {
                    helper.assertTrue(simian.stormSimianCounters().routineStretchResets() >= 1L,
                        "re entering the routine band resets the inherited ledger exactly once");
                    helper.assertValueEqual(
                        simian.stormSimianState().route().consecutiveFailures(), 0,
                        "the failure run belonged to the interrupted stretch and is not inherited");
                    helper.assertTrue(simian.stormSimianState().route().backoffTicks() > 0,
                        "the open backoff window describes the neighbourhood and survives the"
                            + " boundary; backoff="
                            + simian.stormSimianState().route().backoffTicks());
                    helper.assertValueEqual(simian.stormSimianCounters().routeRequests(),
                        frozenAt.get(),
                        "clearing the failure run must not reopen requests early");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(220L, () -> {
                try {
                    helper.assertTrue(
                        simian.stormSimianCounters().routeRequests() > frozenAt.get(),
                        "requests resume once the window has genuinely run out; frozen="
                            + frozenAt.get() + ", now="
                            + simian.stormSimianCounters().routeRequests());
                    helper.assertTrue(
                        simian.stormSimianCounters().routeRequests() - frozenAt.get() <= 6L,
                        "resumed requests are still paced at one per twenty ticks; resumed="
                            + (simian.stormSimianCounters().routeRequests() - frozenAt.get()));
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- troop alarm

    /**
     * The owner is the attacker, so the frozen companion contract forbids targeting them and the
     * alarm is reached without any illegal target ever existing. The recipients receive awareness
     * and nothing else: no target, no relay, no second alarm.
     */
    public static void stormSimianAlarmIsLocalAndLegal(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final StormSimianEntity caller = spawnSimian(fixture, new BlockPos(0, 1, 0));
            final StormSimianEntity neighbour = spawnSimian(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer owner =
                fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.SURVIVAL);
            CreatureBehaviorState.bind(caller, owner.getUUID());

            helper.runAfterDelay(10L, () -> {
                try {
                    helper.assertFalse(caller.canAttack(owner),
                        "the frozen companion contract forbids targeting the bound owner");
                    caller.hurtServer(helper.getLevel(),
                        helper.getLevel().damageSources().playerAttack(owner), 2.0F);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(40L, () -> {
                try {
                    helper.assertValueEqual(caller.stormSimianCounters().alarmsRaised(), 1L,
                        "one legal direct attacker produces exactly one alarm");
                    helper.assertTrue(caller.getTarget() == null,
                        "the alarm never creates a target the companion contract forbids");
                    helper.assertTrue(caller.stormSimianCounters().alarmLineOfSightChecks()
                            <= StormSimianRules.ALARM_LINE_OF_SIGHT_CAP,
                        "line of sight traces are charged and capped at four");
                    helper.assertTrue(caller.stormSimianTransient().lastAlarmRecipients() >= 1
                            && caller.stormSimianTransient().lastAlarmRecipients()
                                <= StormSimianRules.ALARM_RECIPIENT_CAP,
                        "the one loaded neighbour is alerted and the cap is four; recipients="
                            + caller.stormSimianTransient().lastAlarmRecipients());
                    helper.assertTrue(caller.stormSimianState().cooldowns().alarmTicks() > 0,
                        "the alarm arms its own cooldown whatever it found");
                    helper.assertTrue(caller.stormSimianTransient().rememberedAttacker().isEmpty(),
                        "the remembered attacker is spent by the alarm, not left to fire again");
                    helper.assertTrue(neighbour.stormSimianCounters().alarmsReceived() >= 1L,
                        "the neighbour genuinely received the alarm");
                    helper.assertTrue(neighbour.stormSimianTransient().awarenessTicks() > 0,
                        "awareness is what a recipient gains");
                    helper.assertTrue(neighbour.getTarget() == null,
                        "a recipient never inherits a target");
                    helper.assertValueEqual(neighbour.stormSimianCounters().alarmsRaised(), 0L,
                        "a recipient never relays the alarm onward");
                    helper.assertFalse(neighbour.canAttack(owner)
                            && neighbour.getLastHurtByMob() == owner,
                        "a recipient never acquires the caller's attacker as its own");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(250L, () -> {
                try {
                    helper.assertValueEqual(caller.stormSimianCounters().alarmsRaised(), 1L,
                        "the cooldown prevents a second alarm from the same single blow");
                    helper.assertValueEqual(neighbour.stormSimianTransient().awarenessTicks(), 0,
                        "awareness is finite and decays on loaded ticks");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- storm observation

    /**
     * Weather is global, so this fixture records the entire weather state on entry and restores it
     * on close. It asserts the charge is read from the storm and that nothing about the storm, the
     * rod or the world changed as a result.
     */
    public static void stormSimianStormObservationMutatesNoWorldState(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.captureWeather();
            fixture.placeBlock(new BlockPos(2, 0, 2), Blocks.STONE);
            // Waxing removes vanilla random-tick oxidation from a fixture whose contract is that
            // the Simian itself never mutates or energizes the rod.
            fixture.placeBlock(new BlockPos(2, 1, 2), Blocks.LIGHTNING_ROD.waxed().unaffected());
            final BlockState rodBefore = helper.getBlockState(new BlockPos(2, 1, 2));
            fixture.setStorm();
            final StormSimianEntity simian = spawnSimian(fixture, new BlockPos(0, 1, 0));
            simian.setStormSimianState(simian.stormSimianState()
                .withGrip(helper.absolutePos(new BlockPos(0, 1, 0))));

            helper.runAfterDelay(150L, () -> {
                try {
                    final StormSimianRuntime.Counters counters = simian.stormSimianCounters();
                    helper.assertTrue(counters.observationsStarted() >= 1L,
                        "a loaded simian in a thunderstorm reaches the observation concern");
                    helper.assertTrue(counters.observationsCompleted() >= 1L,
                        "an uninterrupted observation window completes");
                    helper.assertValueEqual(simian.stormSimianState().observationEpoch(),
                        counters.observationsCompleted(),
                        "the persisted epoch counts completed windows and nothing else");
                    helper.assertValueEqual(simian.stormSimianState().charge(),
                        (int) (StormSimianRules.THUNDER_CHARGE_GAIN
                            * counters.observationsCompleted()),
                        "charge is exactly one thunder step per completed epoch, never a catch up");
                    helper.assertTrue(simian.stormSimianState().charge()
                            <= StormSimianRules.MAX_CHARGE,
                        "charge is bounded at one hundred");

                    helper.assertValueEqual(counters.weatherWrites(), 0L,
                        "the species reads weather and never writes it");
                    helper.assertValueEqual(counters.blockWrites(), 0L,
                        "the species never edits the world");
                    helper.assertTrue(helper.getLevel().getWeatherData().isThundering(),
                        "observing a storm never ends it");
                    helper.assertValueEqual(helper.getBlockState(new BlockPos(2, 1, 2)),
                        rodBefore,
                        "the lightning rod is never energized, powered, moved or replaced");
                    helper.assertFalse(helper.getBlockState(new BlockPos(2, 1, 2))
                            .getValue(LightningRodBlock.POWERED),
                        "no rod anywhere is switched on by a simian watching a storm");
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(
                            net.minecraft.world.entity.LightningBolt.class,
                            simian.getBoundingBox().inflate(16.0)).isEmpty(),
                        "no lightning is ever created or redirected");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- curiosity

    /** Inspection is movement and attention only. The stack is not touched by any part of it. */
    public static void stormSimianCuriosityDoesNotMoveOrTakeItems(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.captureWeather();
            fixture.setClear();
            final StormSimianEntity simian = spawnSimian(fixture, new BlockPos(0, 1, 0));
            simian.setStormSimianState(simian.stormSimianState()
                .withGrip(helper.absolutePos(new BlockPos(0, 1, 0))));
            final ItemEntity object = fixture.unclaimableItem(new BlockPos(1, 1, 1),
                new ItemStack(Items.DIAMOND, 3));
            final Vec3 objectPosition = object.position();

            helper.runAfterDelay(120L, () -> {
                try {
                    final StormSimianRuntime.Counters counters = simian.stormSimianCounters();
                    helper.assertTrue(counters.curiosityScans() >= 1L,
                        "a simian holding its grip reaches the curiosity concern");
                    helper.assertTrue(counters.curiosityCandidateVisits() >= 1L,
                        "the one loose stack in range was genuinely looked at");
                    helper.assertTrue(counters.curiosityCandidateVisits()
                            <= StormSimianRules.CURIOSITY_INSPECT_CAP * counters.curiosityScans(),
                        "at most four candidates are inspected per scan; visits="
                            + counters.curiosityCandidateVisits());

                    helper.assertFalse(simian.canPickUpLoot(),
                        "vanilla contact pickup is off, so curiosity cannot become theft");
                    helper.assertTrue(object.isAlive(),
                        "the inspected stack is never discarded");
                    helper.assertValueEqual(object.getItem().getCount(), 3,
                        "the inspected stack is never shrunk or consumed");
                    helper.assertTrue(object.getItem().is(Items.DIAMOND),
                        "the inspected stack is never swapped");
                    helper.assertTrue(object.position().distanceToSqr(objectPosition) < 1.0E-6,
                        "the inspected stack is never moved; before=" + objectPosition
                            + ", after=" + object.position());
                    for (final EquipmentSlot slot : EquipmentSlot.values()) {
                        helper.assertTrue(simian.getItemBySlot(slot).isEmpty(),
                            "curiosity never puts anything in " + slot);
                    }
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- charged gust

    /**
     * The gust itself is unchanged: one owned wind charge, one already legal hostile target, the
     * shared ranged attack goal. Charge only changes how it looks and how hard it is thrown, and it
     * is consumed exactly once per attack until there is not enough of it left.
     */
    public static void stormSimianChargedGustConsumesOnce(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.captureWeather();
            fixture.setClear();
            final StormSimianEntity simian = spawnSimian(fixture, new BlockPos(0, 1, 0));
            simian.setStormSimianState(
                simian.stormSimianState().withCharge(StormSimianRules.MAX_CHARGE));
            final Zombie hostile = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2),
                EntitySpawnReason.EVENT);
            hostile.setNoAi(true);

            helper.runAfterDelay(200L, () -> {
                try {
                    final StormSimianRuntime.Counters counters = simian.stormSimianCounters();
                    final long gusts = counters.chargedGusts() + counters.plainGusts();
                    helper.assertTrue(gusts >= 1L,
                        "the shared ranged attack goal still fires the owned wind charge");
                    helper.assertTrue(counters.chargedGusts() >= 1L,
                        "a fully charged simian spends charge on its first legal attack");
                    helper.assertValueEqual(counters.chargeSpent(),
                        StormSimianRules.CHARGED_GUST_COST * counters.chargedGusts(),
                        "each charged gust costs exactly one charge unit, never two");
                    helper.assertTrue(counters.chargedGusts()
                            <= StormSimianRules.MAX_CHARGE / StormSimianRules.CHARGED_GUST_COST,
                        "a hundred charge can pay for at most two charged gusts; charged="
                            + counters.chargedGusts());
                    helper.assertValueEqual(simian.stormSimianState().charge(),
                        StormSimianRules.MAX_CHARGE
                            - (int) counters.chargeSpent()
                            - StormSimianRules.CLEAR_CHARGE_DECAY
                                * (int) counters.observationsCompleted(),
                        "the persisted charge accounts separately for gust spending and legal "
                            + "clear-weather observation decay");
                    helper.assertTrue(simian.stormSimianState().charge()
                            < StormSimianRules.CHARGED_GUST_COST
                        || counters.plainGusts() == 0L,
                        "a plain gust is only ever thrown once the charge is genuinely too low");
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(
                            net.minecraft.world.entity.LightningBolt.class,
                            simian.getBoundingBox().inflate(16.0)).isEmpty(),
                        "a charged gust is still a wind charge, never a lightning strike");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- persistence

    /**
     * The original is discarded before the copy enters, because saved data carries the original
     * UUID and {@code addFreshEntity} silently rejects a duplicate, which would make every
     * assertion below vacuous. The entry is asserted rather than assumed for the same reason.
     */
    public static void stormSimianReloadClearsTransientClaims(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final AtomicReference<StormSimianEntity> reloadedRef = new AtomicReference<>();
        try {
            final StormSimianEntity simian = spawnSimian(fixture, new BlockPos(0, 1, 0));

            helper.runAfterDelay(20L, () -> {
                try {
                    simian.setStormSimianState(new StormSimianState(
                        StormSimianState.SCHEMA_VERSION, 88,
                        java.util.Optional.of(helper.absolutePos(new BlockPos(1, 1, 0))), 240,
                        new StormSimianState.Cooldowns(90, 100,
                            StormSimianRules.OBSERVATION_COOLDOWN_TICKS),
                        new StormSimianState.Route(4, 2, 30), 7L
                    ));
                    final StormSimianRuntime.TransientState scratch = simian.stormSimianTransient();
                    StormSimianRuntime.onAcceptedDamage(simian,
                        helper.getLevel().damageSources().mobAttack(simian));
                    StormSimianRuntime.receiveAlarm(simian);
                    helper.assertTrue(scratch.awarenessTicks() > 0,
                        "the transient facts this fixture is about must genuinely be set first");

                    final TagValueOutput output = TagValueOutput.createWithoutContext(
                        ProblemReporter.DISCARDING);
                    simian.save(output);
                    final CompoundTag saved = output.buildResult();
                    simian.discard();

                    @SuppressWarnings("unchecked")
                    final EntityType<StormSimianEntity> type =
                        (EntityType<StormSimianEntity>) ModEntities.ALL.get("storm_simian").get();
                    final StormSimianEntity reloaded =
                        type.create(helper.getLevel(), EntitySpawnReason.LOAD);
                    helper.assertTrue(reloaded != null,
                        "the registered type must recreate saved state");
                    reloaded.load(TagValueInput.create(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
                    final BlockPos absolute = helper.absolutePos(new BlockPos(0, 1, 2));
                    reloaded.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
                    reloaded.setDeltaMovement(Vec3.ZERO);
                    final boolean entered = helper.getLevel().addFreshEntity(reloaded);
                    fixture.track(reloaded);
                    reloadedRef.set(reloaded);
                    helper.assertTrue(entered,
                        "the reloaded simian must actually enter the level: a rejected entity never"
                            + " ticks and would make every assertion below vacuous");
                    helper.assertValueEqual(helper.getLevel().getEntity(reloaded.getUUID()),
                        reloaded, "the reloaded simian is the one the level now ticks");

                    helper.assertValueEqual(reloaded.stormSimianState().charge(), 88,
                        "semantic charge survives the reload exactly");
                    helper.assertValueEqual(reloaded.stormSimianState().observationEpoch(), 7L,
                        "the completed epoch count survives so a reload is not a fresh observation");
                    helper.assertTrue(reloaded.stormSimianState().grip().isPresent(),
                        "the one semantic grip survives");
                    helper.assertValueEqual(
                        reloaded.stormSimianState().route().consecutiveFailures(), 2,
                        "the route ledger survives; only an episode boundary clears it");
                    helper.assertTrue(reloaded.stormSimianTransient().openWindow().isEmpty(),
                        "no open alarm, inspection or observation window survives a load");
                    helper.assertTrue(reloaded.stormSimianTransient().inspectedObject().isEmpty(),
                        "no inspected object claim survives a load");
                    helper.assertTrue(reloaded.stormSimianTransient().rememberedAttacker().isEmpty(),
                        "no remembered attacker survives a load, so no alarm can be replayed");
                    helper.assertValueEqual(reloaded.stormSimianTransient().awarenessTicks(), 0,
                        "no awareness survives a load");
                    helper.assertTrue(reloaded.getTarget() == null,
                        "no live target survives a load");
                    helper.assertFalse(reloaded.canPickUpLoot(),
                        "the pickup permission stays off across a load");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(100L, () -> {
                try {
                    final StormSimianEntity reloaded = reloadedRef.get();
                    helper.assertTrue(reloaded != null, "the reloaded simian must exist");
                    final StormSimianRuntime.Counters counters = reloaded.stormSimianCounters();
                    helper.assertTrue(counters.decisions() > 0L,
                        "the reloaded simian is genuinely ticking, so these zeroes mean something");
                    helper.assertValueEqual(counters.alarmsRaised(), 0L,
                        "a reload never replays the alarm the saved attacker would have armed");
                    helper.assertValueEqual(counters.observationsCompleted(), 0L,
                        "a reload never completes an observation window it did not open");
                    helper.assertValueEqual(counters.chargedGusts(), 0L,
                        "a reload never replays a charged gust");
                    helper.assertValueEqual(reloaded.stormSimianState().charge(), 88,
                        "no offline or reload catch up ever changes the charge");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- frozen contracts

    /** Everything the redesign promised not to touch, asserted through a live bound companion. */
    public static void stormSimianPreservesOwnerSupport(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final StormSimianEntity simian = spawnSimian(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer owner =
                fixture.connectedPlayer(new BlockPos(1, 1, 1), GameType.SURVIVAL);
            CreatureBehaviorState.bind(simian, owner.getUUID());
            owner.removeEffect(MobEffects.SLOW_FALLING);

            helper.runAfterDelay(60L, () -> {
                try {
                    helper.assertTrue(owner.hasEffect(MobEffects.SLOW_FALLING),
                        "the frozen owner aura still pulses, so the arbiter did not displace the"
                            + " shared companion pipeline it runs after");
                    helper.assertTrue(CreatureBehaviorState.isOwnedBy(simian, owner.getUUID()),
                        "binding is unchanged");
                    helper.assertFalse(simian.canAttack(owner),
                        "the owner is never a legal target");
                    final CreatureBehaviorProfile profile =
                        CreatureBehaviorProfile.find(CreatureKind.STORM_SIMIAN).orElseThrow();
                    helper.assertTrue(profile.has(Feature.FAMILIAR_BOND), "the familiar bond is frozen");
                    helper.assertTrue(profile.has(Feature.WAYSTONE_TRAVEL), "waystone travel is frozen");
                    helper.assertTrue(profile.has(Feature.OWNER_AURA), "the owner aura is frozen");
                    helper.assertTrue(profile.has(Feature.PROTECT_OWNER), "owner protection is frozen");
                    helper.assertTrue(simian.getAttribute(Attributes.FLYING_SPEED) != null,
                        "a flying move control and a flying navigation both read FLYING_SPEED");
                    helper.assertValueEqual(
                        simian.getAttributeValue(Attributes.FOLLOW_RANGE), 32.0,
                        "the random follow range spawn bonus is stripped so the baseline is exact");
                    helper.assertTrue(simian.stormSimianCounters().decisions() > 0L,
                        "the species arbiter genuinely ran alongside all of the above");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    /** The negative suite: no Owl, Steed, familiar or Imp system leaks into this species. */
    public static void stormSimianExcludesOwlSteedFamiliarAndImpSystems(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final StormSimianEntity simian = spawnSimian(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer player =
                fixture.connectedPlayer(new BlockPos(1, 1, 1), GameType.SURVIVAL);

            helper.runAfterDelay(40L, () -> {
                try {
                    helper.assertFalse(ImpEntity.class.isInstance(simian),
                        "no Imp contract, barter, favour or infernal authority");
                    helper.assertFalse(SpiritMob.class.isInstance(simian),
                        "no shared spirit identity");
                    helper.assertFalse(SpectralEntity.class.isInstance(simian),
                        "no spectral binding base");
                    helper.assertValueEqual(StormSimianEntity.class.getSuperclass(),
                        WingedArcaneMob.class,
                        "the species keeps its registered winged base exactly");

                    helper.assertFalse(player.startRiding(simian),
                        "no Steed passenger, gait, fatigue or safe dismount semantics");
                    helper.assertTrue(simian.getPassengers().isEmpty(),
                        "nothing ever rides a Storm Simian");

                    final CreatureBehaviorProfile profile =
                        CreatureBehaviorProfile.find(CreatureKind.STORM_SIMIAN).orElseThrow();
                    helper.assertFalse(profile.has(Feature.ITEM_DELIVERY),
                        "no Owl courier or parcel delivery");
                    helper.assertFalse(profile.has(Feature.BROOM_AURA),
                        "no Owl broom aura");
                    helper.assertFalse(profile.has(Feature.RIDEABLE_BOND),
                        "no Steed rideable bond");
                    helper.assertFalse(profile.has(Feature.FIRE_MELEE),
                        "no Imp or infernal fire melee");
                    helper.assertFalse(profile.has(Feature.ORE_GUIDANCE),
                        "no spectral familiar ore guidance");

                    final List<AmbientActivityProfile.ActivityType> ambient =
                        AmbientActivityProfile.forKind(CreatureKind.STORM_SIMIAN).stream()
                            .map(AmbientActivityProfile::type)
                            .toList();
                    helper.assertValueEqual(ambient,
                        List.of(AmbientActivityProfile.ActivityType.STORM_ROD),
                        "the recognizable rain and lightning rod interest is the only generic"
                            + " ambient routine this species has, and it is deliberately kept");
                    helper.assertFalse(ambient.contains(
                        AmbientActivityProfile.ActivityType.NIGHT_PERCH),
                        "no Owl perch or roost");
                    helper.assertFalse(ambient.contains(
                        AmbientActivityProfile.ActivityType.HAY_REST),
                        "no Steed hay rest");
                    helper.assertFalse(ambient.contains(
                        AmbientActivityProfile.ActivityType.SHINY_CURIOSITY),
                        "no Imp shiny hoard");
                    helper.assertFalse(ambient.contains(
                        AmbientActivityProfile.ActivityType.FAMILIAR_HOME),
                        "no generic familiar home routine");

                    helper.assertValueEqual(
                        TacticalCombatRules.profile(CreatureKind.STORM_SIMIAN).doctrine(),
                        TacticalCombatRules.Doctrine.AERIAL,
                        "the audited aerial combat doctrine is unchanged");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- fixture support

    private static StormSimianEntity spawnSimian(
        final FixtureScope fixture,
        final BlockPos position
    ) {
        @SuppressWarnings("unchecked")
        final EntityType<StormSimianEntity> type =
            (EntityType<StormSimianEntity>) ModEntities.ALL.get("storm_simian").get();
        final StormSimianEntity simian = fixture.spawn(type, position, EntitySpawnReason.EVENT);
        simian.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = fixture.helper.absolutePos(position);
        simian.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        return simian;
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

        /** A loose item no mock player in the arena can remove before the fixture asserts on it. */
        private ItemEntity unclaimableItem(final BlockPos position, final ItemStack stack) {
            final BlockPos absolute = helper.absolutePos(position);
            final ItemEntity item = new ItemEntity(helper.getLevel(),
                absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, stack);
            item.setPickUpDelay(600);
            item.setNoGravity(true);
            item.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(item);
            return track(item);
        }

        /** An exact state placed where it needs no support of its own, restored on close. */
        private void placeState(final BlockPos position, final BlockState state) {
            final BlockPos absolute = helper.absolutePos(position);
            final BlockState previous = helper.getLevel().getBlockState(absolute);
            helper.getLevel().setBlock(absolute, state, 3);
            onClose(() -> helper.getLevel().setBlock(absolute, previous, 3));
        }

        private void placeBlock(
            final BlockPos position,
            final net.minecraft.world.level.block.Block block
        ) {
            final BlockPos absolute = helper.absolutePos(position);
            // The sealed cell has no interior floor at relative y=0, so a block that needs support
            // is popped off by the neighbour update that setBlock triggers. Give it one first, and
            // restore both on close.
            final BlockPos absoluteSupport = absolute.below();
            final BlockState previousSupport = helper.getLevel().getBlockState(absoluteSupport);
            if (previousSupport.isAir()) {
                helper.getLevel().setBlock(absoluteSupport, Blocks.STONE.defaultBlockState(), 3);
                onClose(() -> helper.getLevel().setBlock(absoluteSupport, previousSupport, 3));
            }
            final BlockState previous = helper.getLevel().getBlockState(absolute);
            helper.getLevel().setBlock(absolute, block.defaultBlockState(), 3);
            onClose(() -> helper.getLevel().setBlock(absolute, previous, 3));
        }

        /**
         * Weather and the world clock are global to the level and shared by every fixture in the
         * batch, so the whole weather state is recorded and restored rather than assumed.
         */
        private void captureWeather() {
            final ServerLevel level = helper.getLevel();
            final var weather = level.getWeatherData();
            final boolean raining = weather.isRaining();
            final boolean thundering = weather.isThundering();
            final int rainTime = weather.getRainTime();
            final int thunderTime = weather.getThunderTime();
            final int clearTime = weather.getClearWeatherTime();
            onClose(() -> {
                final var restore = level.getWeatherData();
                restore.setRaining(raining);
                restore.setThundering(thundering);
                restore.setRainTime(rainTime);
                restore.setThunderTime(thunderTime);
                restore.setClearWeatherTime(clearTime);
            });
        }

        private void setStorm() {
            final var weather = helper.getLevel().getWeatherData();
            weather.setClearWeatherTime(0);
            weather.setRaining(true);
            weather.setThundering(true);
            weather.setRainTime(12_000);
            weather.setThunderTime(12_000);
        }

        private void setClear() {
            final var weather = helper.getLevel().getWeatherData();
            weather.setClearWeatherTime(12_000);
            weather.setRaining(false);
            weather.setThundering(false);
            weather.setRainTime(0);
            weather.setThunderTime(0);
        }

        private ServerPlayer connectedPlayer(final BlockPos position, final GameType gameType) {
            final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(gameType);
            final net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(
                    net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            final net.minecraft.server.network.CommonListenerCookie cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(
                    player.getGameProfile(), false);
            helper.getLevel().getServer().getPlayerList()
                .placeNewPlayer(connection, player, cookie);
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
            // 4aab0a9: Entity.discard does not deregister a ServerPlayer, so a merely discarded
            // mock player keeps being reported by ServerLevel.players() for the rest of the run and
            // eats the bounded candidate budget of every later family's acquisition sweep. This
            // family was written against 69d43b8, before that fix, so it releases rather than
            // discards.
            entities.forEach(GameTestMockPlayers::release);
            entities.clear();
            for (int index = cleanupActions.size() - 1; index >= 0; index--) {
                cleanupActions.get(index).run();
            }
            cleanupActions.clear();
        }
    }
}
