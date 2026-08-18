package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.PoltergeistRules.Phase;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Six bounded live F20 fixtures. Every fixture asserts through spawned, AI-enabled, self-ticking
 * Poltergeists: no fixture calls {@link PoltergeistRuntime#tick} by hand, and every assertion runs
 * inside a {@code runAfterDelay} callback registered from the top level of its own test so no
 * callback ever registers another one. Each fixture cleans up every created entity and block in
 * {@code finally}, including mid-sequence stages, and uses exact state and counter assertions
 * instead of elapsed-time guesses.
 *
 * <p>Arena geometry: the framework seals the {@code forge:empty3x3x3} cell in a barrier shell, so
 * every entity, bell and computed destination in these fixtures stays inside relative 0..2 at y=1.
 * The mark band is deliberately 1..3 blocks and the lift range five, so the disturbance reaches a
 * real band without ever routing toward a point beyond the shell and freezing on stale state.</p>
 *
 * <p>These fixtures depend on the coordinator-deferred ModEntities and ModGameTests wiring to route
 * {@code warlockery:poltergeist} through {@link PoltergeistEntity} and to register these six
 * functions.</p>
 */
public final class PoltergeistGameTests {
    /**
     * The latest tick at which every reachable discovery stagger has already produced a lift. The
     * discovery cadence is forty ticks and is staggered per UUID, so the worst case is
     * {@code 39 (stagger) + 40 (rattle) + 1 (mark) + 2 (lift entry)}.
     */
    private static final long LIFT_OBSERVED_AT = 90L;
    /**
     * The earliest tick at which every reachable stagger has already finished the whole episode:
     * {@code 39 + 40 + 1 + 40 + 40 + 60} rounded up, well inside the six-hundred-tick cooldown that
     * forbids a second episode.
     */
    private static final long EPISODE_FINISHED_AT = 300L;
    /**
     * Late enough that every reachable stagger has already opened its throw window, early enough
     * that the resulting {@code getLastHurtByMob} record has not yet timed out.
     */
    private static final long ATTRIBUTION_OBSERVED_AT = 175L;

    private PoltergeistGameTests() {
    }

    // ---------------------------------------------------------------- the disturbance chain

    public static void poltergeistWarnsLiftsThrowsOnceThenRecovers(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final PoltergeistEntity poltergeist = spawnPoltergeist(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer target =
                fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.SURVIVAL);
            final ItemEntity prop = fixture.dropItem(new BlockPos(1, 1, 1),
                new ItemStack(Items.DIAMOND, 3));
            fixture.placeBlock(new BlockPos(0, 1, 1), Blocks.BELL);
            final float targetHealth = target.getHealth();

            helper.runAfterDelay(LIFT_OBSERVED_AT, () -> {
                try {
                    helper.assertValueEqual(poltergeist.poltergeistCounters().episodesStarted(), 1L,
                        "a live self-ticking Poltergeist opens exactly one disturbance episode");
                    helper.assertTrue(
                        poltergeist.poltergeistState().phase() == Phase.LIFT
                            || poltergeist.poltergeistState().phase() == Phase.THROW,
                        "the telegraphed chain has reached its lift or throw window; phase="
                            + poltergeist.poltergeistState().phase());
                    helper.assertTrue(poltergeist.poltergeistCounters().rattlePulses() >= 1L
                            && poltergeist.poltergeistCounters().rattlePulses()
                                <= PoltergeistRules.MAX_RATTLE_PULSES,
                        "the rattle telegraph is visible and capped at three pulses per episode");
                    helper.assertValueEqual(poltergeist.poltergeistCounters().bellRings(), 1L,
                        "exactly one already-loaded nearby bell is rung per episode");
                    helper.assertValueEqual(
                        helper.getBlockState(new BlockPos(0, 1, 1)).getBlock(), Blocks.BELL,
                        "ringing the bell never writes its block state");
                    helper.assertValueEqual(poltergeist.poltergeistCounters().lifts(), 1L,
                        "exactly one short levitation window is applied per episode");
                    helper.assertValueEqual(
                        poltergeist.poltergeistState().episode().lifts(), PoltergeistRules.MAX_LIFTS,
                        "the spent lift is recorded in the persisted episode");
                    helper.assertTrue(poltergeist.getTarget() == null,
                        "the marked player is never written to Mob.target");
                    helper.assertTrue(poltergeist.poltergeistTransient().markedTarget()
                            .filter(target.getUUID()::equals).isPresent(),
                        "the one loaded eligible player is the marked target");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            // Attribution is checked here rather than at the end of the fixture, and through
            // getLastHurtByMob rather than getLastDamageSource, for two measured reasons. The
            // sealed arena levitates the target into the barrier shell, so suffocation becomes the
            // most recent damage source long before the fixture ends and swamped the original
            // assertion. getLastHurtByMob is only written by a living attacker, so neither
            // suffocation nor a fall can overwrite it, and 175 is inside its hundred-tick window
            // for every reachable discovery stagger (the throw lands between tick 84 and 124).
            helper.runAfterDelay(ATTRIBUTION_OBSERVED_AT, () -> {
                try {
                    helper.assertValueEqual(poltergeist.poltergeistCounters().throwHits(), 1L,
                        "the throw window landed its one permitted hit");
                    helper.assertValueEqual(target.getLastHurtByMob(), poltergeist,
                        "the hit is attributed to the Poltergeist that caused it, never to the "
                            + "player who dropped the prop and never to the arena");
                    helper.assertTrue(target.getHealth() < targetHealth,
                        "the one permitted hit lands as real damage; before=" + targetHealth
                            + ", after=" + target.getHealth());
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(EPISODE_FINISHED_AT, () -> {
                try {
                    helper.assertValueEqual(poltergeist.poltergeistState().phase(), Phase.LURK,
                        "the whole episode is finite and returns to lurking");
                    helper.assertValueEqual(poltergeist.poltergeistCounters().episodesEnded(), 1L,
                        "the episode end is counted exactly once");
                    helper.assertValueEqual(poltergeist.poltergeistCounters().velocityWrites(), 1L,
                        "the prop receives exactly one velocity write per episode");
                    helper.assertValueEqual(poltergeist.poltergeistCounters().throwHits(), 1L,
                        "at most one separately attributed hit is permitted per episode");
                    helper.assertValueEqual(poltergeist.poltergeistCounters().recoveries(), 1L,
                        "the lifted target receives exactly one bounded safe-landing recovery");
                    helper.assertTrue(target.hasEffect(MobEffects.SLOW_FALLING),
                        "the recovery turns the lift into displacement instead of fall damage");
                    helper.assertTrue(
                        poltergeist.poltergeistState().cadence().cooldownTicks() > 0,
                        "the long cooldown prevents an immediate second episode");
                    helper.assertTrue(poltergeist.poltergeistTransient().markedTarget().isEmpty()
                            && poltergeist.poltergeistTransient().markedProp().isEmpty(),
                        "the recovery clears the transient target and prop claims");
                    helper.assertTrue(prop.isAlive() && prop.getItem().getCount() == 3
                            && prop.getItem().is(Items.DIAMOND),
                        "the thrown prop is still the exact stack the player dropped");
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

    public static void poltergeistMissingOrPickedPropFinishesSafely(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final PoltergeistEntity poltergeist = spawnPoltergeist(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer target =
                fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.SURVIVAL);
            final ItemEntity prop = fixture.dropItem(new BlockPos(1, 1, 1),
                new ItemStack(Items.DIAMOND, 1));

            // Sixty ticks is after every reachable stagger has selected the prop and before the
            // earliest possible lift-to-throw revalidation, so the prop is genuinely claimed and
            // then genuinely gone.
            helper.runAfterDelay(60L, () -> {
                try {
                    helper.assertTrue(poltergeist.poltergeistTransient().markedProp()
                            .filter(prop.getUUID()::equals).isPresent(),
                        "the one loaded loose item was claimed as the episode prop");
                    prop.discard();
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(EPISODE_FINISHED_AT, () -> {
                try {
                    helper.assertValueEqual(poltergeist.poltergeistCounters().velocityWrites(), 0L,
                        "a prop that was picked up or removed is never thrown");
                    helper.assertValueEqual(poltergeist.poltergeistCounters().throwHits(), 0L,
                        "no hit can occur without a prop");
                    helper.assertValueEqual(poltergeist.poltergeistCounters().lifts(), 1L,
                        "the episode still finishes lift-only rather than being abandoned");
                    helper.assertValueEqual(poltergeist.poltergeistCounters().propScans(), 1L,
                        "a lost prop never opens a second scan inside the same episode");
                    helper.assertValueEqual(poltergeist.poltergeistCounters().episodesEnded(), 1L,
                        "the lift-only episode ends exactly once");
                    helper.assertValueEqual(poltergeist.poltergeistState().phase(), Phase.LURK,
                        "the lift-only episode returns to lurking");
                    helper.assertTrue(
                        poltergeist.poltergeistState().cadence().cooldownTicks() > 0,
                        "a lift-only finish still arms the long cooldown");
                    helper.assertTrue(target.hasEffect(MobEffects.SLOW_FALLING),
                        "a lift-only finish still lands the lifted target safely");
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

    public static void poltergeistThrowPreservesItemStackAndPickup(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final PoltergeistEntity poltergeist = spawnPoltergeist(fixture, new BlockPos(0, 1, 0));
            fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.SURVIVAL);
            final ItemEntity prop = fixture.dropItem(new BlockPos(1, 1, 1),
                new ItemStack(Items.GOLDEN_APPLE, 5));
            prop.setPickUpDelay(0);
            final ItemStack before = prop.getItem().copy();
            final UUID propId = prop.getUUID();

            helper.runAfterDelay(EPISODE_FINISHED_AT, () -> {
                try {
                    helper.assertValueEqual(poltergeist.poltergeistCounters().velocityWrites(), 1L,
                        "exactly one velocity event is written to the prop");
                    helper.assertTrue(prop.isAlive(),
                        "the prop is never removed, consumed, or replaced");
                    helper.assertValueEqual(prop.getUUID(), propId,
                        "the prop is never copied into a new entity");
                    helper.assertTrue(ItemStack.matches(prop.getItem(), before),
                        "the stack, its count and its components are never mutated; before="
                            + before + ", after=" + prop.getItem());
                    helper.assertFalse(prop.hasPickUpDelay(),
                        "the prop is never locked away from the players who can pick it up");
                    helper.assertTrue(prop.getOwner() == null,
                        "the disturbance never claims ownership of the item it throws");
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

    // ---------------------------------------------------------------- bounds and stability

    public static void poltergeistDenseCandidatesStayCappedAndStable(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            // Layout note: both disturbances sit on the low-x edge and the two eligible players sit
            // so that the nearest one stays nearest from either disturbance's cell even after a
            // block of collision drift, so "same scene, same choice" is a real assertion rather
            // than an accident of where they happened to be standing.
            final PoltergeistEntity first = spawnPoltergeist(fixture, new BlockPos(0, 1, 0));
            final PoltergeistEntity second = spawnPoltergeist(fixture, new BlockPos(0, 1, 1));
            final ServerPlayer nearest =
                fixture.connectedPlayer(new BlockPos(1, 1, 0), GameType.SURVIVAL);
            final ServerPlayer further =
                fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.SURVIVAL);
            final ServerPlayer creative =
                fixture.connectedPlayer(new BlockPos(2, 1, 0), GameType.CREATIVE);
            // Eight DISTINCT items in a cell no player occupies. Eight stacks of one item at a
            // single position merge into one entity and the rest are discarded, and any item within
            // a block of a mock player is picked up on the next tick; either would have failed the
            // "clutter is never scattered or consumed" assertion for reasons that have nothing to
            // do with the Poltergeist. The long pickup delay pins the second case.
            final List<Item> clutterKinds = List.of(Items.STICK, Items.BONE, Items.FEATHER,
                Items.FLINT, Items.PAPER, Items.LEATHER, Items.CLAY_BALL, Items.BRICK);
            final List<ItemEntity> clutter = new ArrayList<>();
            for (final Item kind : clutterKinds) {
                clutter.add(fixture.unclaimableItem(new BlockPos(2, 1, 1), new ItemStack(kind, 1)));
            }
            final ItemEntity nearestProp = fixture.unclaimableItem(new BlockPos(1, 1, 1),
                new ItemStack(Items.GOLD_NUGGET, 1));

            helper.runAfterDelay(LIFT_OBSERVED_AT, () -> {
                try {
                    for (final PoltergeistEntity poltergeist : List.of(first, second)) {
                        helper.assertValueEqual(poltergeist.poltergeistCounters().episodesStarted(),
                            1L, "a dense scene still opens exactly one episode per disturbance");
                        helper.assertTrue(poltergeist.poltergeistCounters().candidateVisits()
                                >= 3L + clutter.size() + 1L,
                            "every returned candidate is charged before any filter can reject it; "
                                + "visits=" + poltergeist.poltergeistCounters().candidateVisits());
                        helper.assertTrue(poltergeist.poltergeistCounters().velocityWrites() <= 1L,
                            "a dense scene never multiplies the one permitted velocity write");
                        helper.assertTrue(poltergeist.poltergeistState().episode().velocityWrites()
                                <= PoltergeistRules.MAX_VELOCITY_WRITES,
                            "the persisted episode records at most one velocity write");
                        helper.assertTrue(poltergeist.poltergeistCounters().lifts() <= 1L,
                            "at most one player is ever lifted per episode");
                        helper.assertTrue(poltergeist.poltergeistCounters().propScans() <= 1L,
                            "a dense scene still opens exactly one prop scan per episode");
                    }
                    helper.assertValueEqual(
                        first.poltergeistTransient().markedTarget().orElse(null),
                        nearest.getUUID(),
                        "stable distance-then-identity ordering marks the nearest eligible player");
                    helper.assertValueEqual(
                        second.poltergeistTransient().markedTarget().orElse(null),
                        first.poltergeistTransient().markedTarget().orElse(null),
                        "two disturbances observing the same scene mark the same player");
                    helper.assertValueEqual(
                        first.poltergeistTransient().markedProp().orElse(null),
                        second.poltergeistTransient().markedProp().orElse(null),
                        "two disturbances observing the same scene claim the same prop");
                    helper.assertValueEqual(
                        first.poltergeistTransient().markedProp().orElse(null),
                        nearestProp.getUUID(),
                        "the claimed prop is the nearest loose item, not the first one iterated");
                    for (final PoltergeistEntity poltergeist : List.of(first, second)) {
                        helper.assertFalse(poltergeist.poltergeistTransient().markedTarget()
                                .filter(creative.getUUID()::equals).isPresent(),
                            "a creative player is visited, charged, and then rejected");
                        helper.assertFalse(poltergeist.poltergeistTransient().markedTarget()
                                .filter(further.getUUID()::equals).isPresent(),
                            "the further eligible player never wins the stable ordering");
                    }
                    helper.assertTrue(clutter.stream().allMatch(item ->
                            item.isAlive() && !item.getItem().isEmpty()),
                        "unclaimed clutter is never scattered, consumed, or mutated");
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

    public static void poltergeistHazardAndThreeRouteFailuresCancel(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final java.util.concurrent.atomic.AtomicLong liftsAtCancellation =
            new java.util.concurrent.atomic.AtomicLong(-1L);
        try {
            final PoltergeistEntity poltergeist = spawnPoltergeist(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer target =
                fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.SURVIVAL);

            helper.runAfterDelay(60L, () -> {
                try {
                    helper.assertTrue(poltergeist.poltergeistState().phase() != Phase.LURK,
                        "the disturbance is genuinely mid episode before the route is exhausted");
                    poltergeist.setPoltergeistState(poltergeist.poltergeistState().withCadence(
                        new PoltergeistState.Cadence(0, PoltergeistRules.MAX_ROUTE_FAILURES, 0)
                    ));
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(66L, () -> {
                try {
                    helper.assertValueEqual(poltergeist.poltergeistState().phase(), Phase.RECOVER,
                        "a third persisted route failure is observed by the live tick and cancels");
                    helper.assertTrue(poltergeist.getNavigation().isDone(),
                        "the cancelled episode leaves no stale navigation");
                    helper.assertTrue(poltergeist.getTarget() == null,
                        "no combat target survives a cancelled episode");
                    liftsAtCancellation.set(poltergeist.poltergeistCounters().lifts());
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(140L, () -> {
                try {
                    helper.assertValueEqual(poltergeist.poltergeistState().phase(), Phase.LURK,
                        "the cancellation still finishes through the one recovery exit");
                    // Not "exactly zero": the release does reset the counter, but the idle drift
                    // that resumes in LURK cannot route anywhere inside the sealed arena, so it
                    // legitimately charges a fresh failure before this assertion runs. What must
                    // hold is that the exhausted state was cleared rather than carried forward.
                    helper.assertTrue(poltergeist.poltergeistState().cadence().routeFailures()
                            < PoltergeistRules.MAX_ROUTE_FAILURES,
                        "the release cleared the exhausted route state; failures="
                            + poltergeist.poltergeistState().cadence().routeFailures());
                    helper.assertTrue(poltergeist.poltergeistState().cadence().cooldownTicks() > 0,
                        "the cancelled episode still arms the long cooldown");
                    poltergeist.igniteForSeconds(6.0F);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(180L, () -> {
                try {
                    helper.assertTrue(poltergeist.poltergeistTransient().hazardActive(),
                        "an escapable hazard is observed by the live tick");
                    helper.assertTrue(poltergeist.poltergeistCounters().hazardInterruptions() >= 1L,
                        "the hazard interruption is counted");
                    helper.assertValueEqual(poltergeist.poltergeistCounters().episodesStarted(), 1L,
                        "a hazard never lets a second episode open behind the cooldown");
                    // "No levitation tail" is asserted as "no lift was applied after the
                    // cancellation", not as "the target carries no levitation effect right now".
                    // The effect form was flaky: whether the cancellation lands before or after the
                    // lift depends on the per-UUID discovery stagger, so the target may legitimately
                    // still be carrying the one window that was already applied. What the runtime
                    // must never do is apply another one once the episode has been cancelled.
                    helper.assertValueEqual(poltergeist.poltergeistCounters().lifts(),
                        liftsAtCancellation.get(),
                        "a cancelled episode never applies another lift; at cancellation="
                            + liftsAtCancellation.get() + ", now="
                            + poltergeist.poltergeistCounters().lifts());
                    helper.assertTrue(poltergeist.poltergeistCounters().lifts()
                            <= PoltergeistRules.MAX_LIFTS,
                        "no episode ever exceeds its one permitted lift");
                    helper.assertTrue(poltergeist.poltergeistCounters().velocityWrites() == 0L,
                        "a cancelled episode never leaves a delayed throw behind");
                    poltergeist.clearFire();
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

    public static void poltergeistReloadDoesNotReplayAndFamiliesStayIsolated(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        final AtomicReference<PoltergeistEntity> reloadedRef = new AtomicReference<>();
        try {
            final PoltergeistEntity poltergeist = spawnPoltergeist(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer target =
                fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.SURVIVAL);
            fixture.dropItem(new BlockPos(1, 1, 1), new ItemStack(Items.DIAMOND, 1));

            helper.runAfterDelay(LIFT_OBSERVED_AT, () -> {
                try {
                    helper.assertTrue(
                        poltergeist.poltergeistState().phase() == Phase.LIFT
                            || poltergeist.poltergeistState().phase() == Phase.THROW,
                        "the saved disturbance is genuinely inside an attack phase; phase="
                            + poltergeist.poltergeistState().phase());
                    final TagValueOutput output = TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
                    );
                    poltergeist.saveWithoutId(output);
                    final var saved = output.buildResult().copy();
                    // The saved NBT carries the original's UUID, so the original has to leave the
                    // level before the reload enters it. Adding both makes the level reject the
                    // second one outright ("UUID of added entity already exists"), and a rejected
                    // entity never ticks, which silently turns every downstream no-replay
                    // assertion vacuous. Discarding first is also what a real unload/reload does.
                    poltergeist.discard();
                    final PoltergeistEntity reloaded = (PoltergeistEntity)
                        ModEntities.ALL.get("poltergeist").get()
                            .create(helper.getLevel(), EntitySpawnReason.LOAD);
                    helper.assertTrue(reloaded != null,
                        "the registered type must recreate saved state");
                    reloaded.load(TagValueInput.create(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved
                    ));
                    final BlockPos absolute = helper.absolutePos(new BlockPos(0, 1, 2));
                    reloaded.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
                    reloaded.setDeltaMovement(Vec3.ZERO);
                    final boolean entered = helper.getLevel().addFreshEntity(reloaded);
                    fixture.track(reloaded);
                    reloadedRef.set(reloaded);
                    helper.assertTrue(entered,
                        "the reloaded disturbance must actually enter the level: a rejected entity "
                            + "never ticks and would make every no-replay assertion vacuous");
                    helper.assertValueEqual(helper.getLevel().getEntity(reloaded.getUUID()),
                        reloaded, "the reloaded disturbance is the one the level now ticks");

                    helper.assertValueEqual(reloaded.poltergeistState().phase(), Phase.RECOVER,
                        "a reload never resumes inside a lift or a throw window");
                    helper.assertTrue(reloaded.poltergeistState().episode().lifts() >= 1,
                        "the spent lift survives the reload so no second lift is ever granted");
                    helper.assertTrue(reloaded.poltergeistTransient().markedTarget().isEmpty()
                            && reloaded.poltergeistTransient().markedProp().isEmpty(),
                        "no saved target or prop reference survives a load");
                    helper.assertTrue(reloaded.getTarget() == null,
                        "no live target survives a load");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(EPISODE_FINISHED_AT, () -> {
                try {
                    final PoltergeistEntity reloaded = reloadedRef.get();
                    helper.assertTrue(reloaded != null, "the reloaded disturbance must exist");
                    helper.assertValueEqual(reloaded.poltergeistCounters().lifts(), 0L,
                        "the reloaded disturbance never replays the lift it already spent");
                    helper.assertValueEqual(reloaded.poltergeistCounters().velocityWrites(), 0L,
                        "the reloaded disturbance never replays a delayed throw");
                    helper.assertValueEqual(reloaded.poltergeistCounters().throwHits(), 0L,
                        "the reloaded disturbance never replays the one permitted hit");
                    helper.assertValueEqual(reloaded.poltergeistCounters().rattlePulses(), 0L,
                        "the reloaded disturbance never replays its telegraph");
                    helper.assertValueEqual(reloaded.poltergeistCounters().bellRings(), 0L,
                        "the reloaded disturbance never replays its bell");
                    helper.assertValueEqual(reloaded.poltergeistState().phase(), Phase.LURK,
                        "the resumed recovery closes through the one episode exit");
                    helper.assertTrue(reloaded.poltergeistState().cadence().cooldownTicks() > 0,
                        "the resumed recovery arms the long cooldown exactly like a live one");
                    helper.assertValueEqual(reloaded.poltergeistCounters().episodesStarted(), 0L,
                        "a resumed recovery never counts as a fresh episode");

                    // Family isolation: the disturbance carries no Vex, SpiritMob, spectral base or
                    // Enemy identity, and no generic writer can contest its movement.
                    for (final PoltergeistEntity subject : List.of(poltergeist, reloaded)) {
                        helper.assertFalse(Vex.class.isInstance(subject),
                            "the dedicated Poltergeist is not a Vex copy");
                        helper.assertFalse(SpiritMob.class.isInstance(subject),
                            "the dedicated Poltergeist carries no shared SpiritMob identity");
                        helper.assertFalse(SpectralEntity.class.isInstance(subject),
                            "the Poltergeist never inherits the F19 spectral binding base");
                        helper.assertFalse(Enemy.class.isInstance(subject),
                            "the Poltergeist is not an Enemy: no golem auto-targeting");
                        helper.assertValueEqual(subject.operationalTargetGoalCount(), 0,
                            "no target goal is ever registered, so no charge goal can return");
                        helper.assertTrue(subject.operationalGoalNames().stream().allMatch(name ->
                                "LookAtPlayerGoal".equals(name)
                                    || "LookOnlyRandomLookGoal".equals(name)),
                            "only look goals remain; movement authority is the runtime's alone: "
                                + subject.operationalGoalNames());
                        helper.assertTrue(subject.getTarget() == null,
                            "the Poltergeist never holds a combat target");
                    }
                    helper.assertFalse(target.hasEffect(MobEffects.DARKNESS),
                        "the Poltergeist never borrows Spectre fear or darkness semantics");
                    // F21 delegated SPECTRE and ECHO_SHADE to their own dedicated runtimes, so
                    // UMBRAL_SIGIL is the last generic soul-lantern vigil family. Retargeted
                    // rather than removed: the intent is that F20's own edit did not collaterally
                    // strip the surviving generic vigil families.
                    helper.assertTrue(
                        AmbientActivityProfile.forKind(
                            ArcaneCreature.CreatureKind.UMBRAL_SIGIL).size() >= 1,
                        "the remaining generic vigil family keeps its own ambient routine");
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

    private static PoltergeistEntity spawnPoltergeist(
        final FixtureScope fixture,
        final BlockPos position
    ) {
        @SuppressWarnings("unchecked")
        final EntityType<PoltergeistEntity> type =
            (EntityType<PoltergeistEntity>) ModEntities.ALL.get("poltergeist").get();
        final PoltergeistEntity poltergeist =
            fixture.spawn(type, position, EntitySpawnReason.EVENT);
        poltergeist.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = fixture.helper.absolutePos(position);
        poltergeist.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        return poltergeist;
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

        /**
         * A stationary loose item. Gravity is disabled so the prop stays at a known distance for
         * the whole fixture; nothing about the stack itself is touched.
         */
        private ItemEntity dropItem(final BlockPos position, final ItemStack stack) {
            return dropItem(position, stack, 0);
        }

        /** A loose item no mock player in the arena can remove before the fixture asserts on it. */
        private ItemEntity unclaimableItem(final BlockPos position, final ItemStack stack) {
            return dropItem(position, stack, 600);
        }

        private ItemEntity dropItem(
            final BlockPos position,
            final ItemStack stack,
            final int pickUpDelay
        ) {
            final BlockPos absolute = helper.absolutePos(position);
            final ItemEntity item = new ItemEntity(helper.getLevel(),
                absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, stack);
            item.setPickUpDelay(pickUpDelay);
            item.setNoGravity(true);
            item.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(item);
            return track(item);
        }

        private void placeBlock(
            final BlockPos position,
            final net.minecraft.world.level.block.Block block
        ) {
            final BlockPos absolute = helper.absolutePos(position);
            // The sealed forge:empty3x3x3 cell has no interior floor at relative y=0, so a block
            // that needs support (a floor-attached bell does) is popped off by the neighbour update
            // that setBlock triggers. Give it a floor first, and restore both on close.
            final BlockPos absoluteSupport = absolute.below();
            final var previousSupport = helper.getLevel().getBlockState(absoluteSupport);
            if (previousSupport.isAir()) {
                helper.getLevel().setBlock(absoluteSupport, Blocks.STONE.defaultBlockState(), 3);
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
            entities.forEach(Entity::discard);
            entities.clear();
            for (int index = cleanupActions.size() - 1; index >= 0; index--) {
                cleanupActions.get(index).run();
            }
            cleanupActions.clear();
        }
    }
}
