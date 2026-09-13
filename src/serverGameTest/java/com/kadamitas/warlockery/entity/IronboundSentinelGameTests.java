package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.IronboundSentinelRules.Charge;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Phase;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Six bounded live F36 fixtures. Every assertion runs through a spawned, AI-enabled, self-ticking
 * Sentinel: no fixture calls {@link IronboundSentinelRuntime#tick} by hand, every assertion runs
 * inside a {@code runAfterDelay} callback registered from the top level of its own test so no
 * callback ever registers another, and every fixture cleans up in {@code finally}.
 *
 * <h2>Isolation</h2>
 *
 * <p>The ward radius is twelve and the retention radius sixteen, both larger than the eight-to-ten
 * block spacing of the GameTest batch grid, so a quadrant sweep genuinely reaches into neighbouring
 * instances. Three separate properties make that harmless and one of them is asserted explicitly:
 * another Ironbound Sentinel is refused at the second rung of the legality function before any
 * distance or sight test; candidates are ordered nearest first so the fixture's own player at about
 * 1.4 blocks always receives the first of the two sight rays; and every neighbouring instance sits
 * behind its own barrier shell, which blocks the sight trace, so nothing outside this cell can ever
 * be bound. Every fixture that binds anything asserts the bound identity by UUID rather than by a
 * count, and says so in its failure message, so contamination fails loudly instead of passing.</p>
 *
 * <p>Arena geometry: the framework seals the {@code forge:empty3x3x3} cell, so every entity and
 * every destination stays inside relative 0..2 at y=1. The Sentinel never paths outside its tether
 * and its station is its own spawn position, so no fixture can ask it to navigate through the shell
 * and stall on stale state.</p>
 *
 * <p>These fixtures depend on the coordinator-deferred ModEntities and ModGameTests wiring to route
 * {@code warlockery:ironbound_sentinel} through {@link IronboundSentinelEntity} and to register
 * these six functions.</p>
 */
public final class IronboundSentinelGameTests {
    /**
     * Where every fixture that wants to be found stands. It is one block along +Z from the station
     * and exactly on the station's X axis, so its hitbox straddles that axis and it therefore lies
     * inside the quadrants of bearing 0 and bearing 1 rather than only one of them. That is what
     * makes {@link #BOUND_BY} a worst case rather than a hope.
     */
    private static final BlockPos INTRUDER = new BlockPos(1, 1, 2);
    /**
     * The worst-case tick by which an intruder standing at {@link #INTRUDER} must have been swept.
     * Two independent UUID-seeded cadences decide when: the sweep runs every twenty ticks from an
     * offset in {@code [1, 20]}, and the bearing advances every sixty ticks from an offset in
     * {@code [1, 60]}. The intruder is inside the quadrants of the first two bearings, so the last
     * tick on which it can still be unswept is the second bearing advance at 120 plus one full
     * sweep period. Anything later than that would mean the sweep is not running at all, which is
     * what this bound is here to catch. Moving it below 140 makes the fixture depend on the entity
     * UUID the run happened to draw.
     */
    private static final long BOUND_BY = 140L;
    /**
     * Late enough for at least one repel attempt after the worst-case binding, and early enough
     * that the resulting {@code getLastHurtByMob} record has not timed out. Valid range: strictly
     * greater than {@code BOUND_BY + 20} and inside the hundred-tick attribution window that
     * follows the first landed hit.
     */
    private static final long REPEL_OBSERVED_AT = BOUND_BY + 45L;
    /** One waking or stand-down transition is sixty loaded ticks; eighty clears every stagger. */
    private static final long TRANSITION_FINISHED_AT = 80L;

    private IronboundSentinelGameTests() {
    }

    // ---------------------------------------------------------------- the charge

    /**
     * The socket act draws a seated charge, the stand-down actually completes, and seating it again
     * runs the waking to completion. Both transitions are asserted through the durable charge and
     * through the counter the tick branch increments, so a transition that the record reconciled
     * away rather than a branch completing would leave the counter at zero and fail here.
     */
    public static void ironboundSentinelChargeWakesStandsDownAndResumes(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final IronboundSentinelEntity sentinel = spawnSentinel(fixture, new BlockPos(1, 1, 1));
            // Deliberately a permitted party. The socket act is refused to the Sentinel's own bound
            // subject, so a survival keeper standing this close would be barred first and could
            // then never reach the act at all, which would make this fixture about the wrong thing.
            final ServerPlayer keeper =
                fixture.connectedPlayer(new BlockPos(2, 1, 1), GameType.CREATIVE);
            final AtomicLong sweepsWhenDrawn = new AtomicLong(-1L);

            helper.assertValueEqual(sentinel.sentinelState().charge(), Charge.CHARGED,
                "the making is the commissioning: a fresh Sentinel is already keeping");

            helper.runAfterDelay(5L, () -> {
                try {
                    sweepsWhenDrawn.set(sentinel.sentinelCounters().sweeps());
                    socket(sentinel, keeper);
                    helper.assertValueEqual(sentinel.sentinelCounters().socketDraws(), 1L,
                        "one deliberate open-handed act draws the seated charge");
                    helper.assertValueEqual(sentinel.sentinelState().charge(),
                        Charge.STANDING_DOWN, "drawing the charge starts the stand-down");
                    helper.assertValueEqual(sentinel.sentinelState().transitionRemaining(), 60,
                        "the stand-down loads its declared sixty loaded ticks");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(5L + TRANSITION_FINISHED_AT, () -> {
                try {
                    helper.assertValueEqual(sentinel.sentinelState().charge(), Charge.INERT,
                        "the stand-down completes rather than stranding on a zero counter");
                    helper.assertValueEqual(sentinel.sentinelCounters().standDowns(), 1L,
                        "the tick branch that ends the transition ran exactly once");
                    helper.assertValueEqual(sentinel.sentinelState().strain(), 0,
                        "entering INERT clears the strain ledger");
                    helper.assertValueEqual(sentinel.phase(), Phase.STILLED,
                        "an inert Sentinel does nothing at all");
                    helper.assertValueEqual(sentinel.sentinelCounters().sweeps(),
                        sweepsWhenDrawn.get(),
                        "not one sweep ran across the whole stand-down and the inert stretch that "
                            + "followed it, so a stood-down keeper genuinely costs nothing");
                    socket(sentinel, keeper);
                    helper.assertValueEqual(sentinel.sentinelCounters().socketSeats(), 1L,
                        "the same act seats the charge again");
                    helper.assertValueEqual(sentinel.sentinelState().charge(), Charge.WAKING,
                        "seating the charge enters the waking rather than jumping to CHARGED");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(5L + 2L * TRANSITION_FINISHED_AT, () -> {
                try {
                    helper.assertValueEqual(sentinel.sentinelState().charge(), Charge.CHARGED,
                        "the waking completes and the Sentinel keeps again");
                    helper.assertValueEqual(sentinel.sentinelCounters().wakings(), 1L,
                        "the waking is completed by its own tick branch exactly once");
                    helper.assertTrue(sentinel.sentinelCounters().sweeps() >= 1L,
                        "a re-seated charge resumes the routine; sweeps="
                            + sentinel.sentinelCounters().sweeps());
                    helper.assertValueEqual(sentinel.station().orElseThrow(),
                        helper.absolutePos(new BlockPos(1, 1, 1)),
                        "the station survives both transitions untouched");
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

    // ---------------------------------------------------------------- the ward

    /**
     * A visible unpermitted party inside the ward is bound, barred and repelled with correct
     * attribution, and the identity it bound is asserted by UUID so a candidate borrowed from a
     * neighbouring instance would fail loudly rather than satisfy the count.
     */
    public static void ironboundSentinelWardBarsAndRepelsOnlyWithinSight(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final IronboundSentinelEntity sentinel = spawnSentinel(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer intruder =
                fixture.connectedPlayer(INTRUDER, GameType.SURVIVAL);

            helper.runAfterDelay(BOUND_BY, () -> {
                try {
                    helper.assertValueEqual(sentinel.sentinelCounters().bindings(), 1L,
                        "the due sweep binds exactly one subject");
                    helper.assertTrue(sentinel.sentinelTransient().boundSubject()
                            .filter(intruder.getUUID()::equals).isPresent(),
                        "the bound subject must be this cell's own player and never a candidate "
                            + "borrowed from a neighbouring instance; bound="
                            + sentinel.sentinelTransient().boundSubject());
                    helper.assertTrue(sentinel.sentinelCounters().candidateVisits()
                            <= IronboundSentinelRules.SWEEP_ENTITY_VISITS
                                * Math.max(1L, sentinel.sentinelCounters().sweeps()),
                        "the capped query never visits more than six raw entities per sweep; "
                            + "visits=" + sentinel.sentinelCounters().candidateVisits()
                            + " sweeps=" + sentinel.sentinelCounters().sweeps());
                    helper.assertTrue(sentinel.sentinelCounters().sightRays()
                            <= IronboundSentinelRules.SWEEP_SIGHT_RAYS
                                * sentinel.sentinelCounters().sweeps()
                                + sentinel.sentinelTransient().episodeTicks()
                                    / IronboundSentinelRules.REVALIDATION_TICKS + 2L,
                        "every sight trace is charged: at most two per sweep plus one per "
                            + "retention cadence; rays=" + sentinel.sentinelCounters().sightRays()
                            + " sweeps=" + sentinel.sentinelCounters().sweeps()
                            + " episodeTicks=" + sentinel.sentinelTransient().episodeTicks());
                    helper.assertTrue(sentinel.phase() == Phase.BAR
                            || sentinel.phase() == Phase.REPEL,
                        "binding enters the episode; phase=" + sentinel.phase());
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(REPEL_OBSERVED_AT, () -> {
                try {
                    helper.assertTrue(sentinel.sentinelCounters().repelHits() >= 1L,
                        "the bound subject is actually repelled; hits="
                            + sentinel.sentinelCounters().repelHits() + " attempts="
                            + sentinel.sentinelCounters().repelAttempts()
                            + " phase=" + sentinel.phase());
                    // Attribution rather than health: the sealed arena can also damage a player
                    // through contact or a fall, and getLastHurtByMob is only ever written by a
                    // living attacker, so neither can forge this.
                    helper.assertValueEqual(intruder.getLastHurtByMob(), sentinel,
                        "the repel is attributed to the Sentinel that landed it, never to the "
                            + "arena and never to a neighbouring instance");
                    // Measured against the fixture's own elapsed ticks, deliberately, and not
                    // against the episode's. An early UUID stagger opens the episode within a few
                    // ticks, which means the subject can be repelled to death and released before
                    // this callback runs, and a released episode reports zero elapsed ticks. The
                    // invariant that actually matters is unaffected by any of that: no more than
                    // one attempt per twenty loaded ticks can ever have happened. Striking every
                    // tick would show about a hundred and eighty attempts here.
                    helper.assertTrue(sentinel.sentinelCounters().repelAttempts()
                            <= REPEL_OBSERVED_AT / IronboundSentinelRules.REPEL_CADENCE_TICKS + 2L,
                        "the twenty-tick repel cadence binds rather than striking every tick; "
                            + "attempts=" + sentinel.sentinelCounters().repelAttempts());
                    helper.assertValueEqual(sentinel.sentinelCounters().seizes(), 0L,
                        "a short episode never reaches the strain cap");
                    helper.assertValueEqual(sentinel.sentinelTransient().routeFailures(), 0,
                        "a Sentinel already standing on its station requests no route it cannot "
                            + "take, so no route failure and no backoff can be invented");
                    helper.assertValueEqual(sentinel.sentinelTransient().routeBackoffRemaining(), 0,
                        "and therefore no backoff window is open");
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
     * The armed control for the rung that is easiest to lose. A creative-mode party is refused at
     * the fourth rung of the legality function, so nothing is bound, nothing is repelled and no
     * strain accrues, while the Sentinel keeps sweeping rather than falling silent.
     */
    public static void ironboundSentinelPermittedPartiesAreNeverBoundOrRepelled(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final IronboundSentinelEntity sentinel = spawnSentinel(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer permitted =
                fixture.connectedPlayer(INTRUDER, GameType.CREATIVE);
            final float health = permitted.getHealth();

            helper.runAfterDelay(BOUND_BY, () -> {
                try {
                    helper.assertValueEqual(sentinel.sentinelCounters().bindings(), 0L,
                        "a creative party is refused before any distance or sight test");
                    // The armed control. Every assertion above is about something that did not
                    // happen, so these three prove the Sentinel was awake, that the capped query
                    // really returned this cell's two living entities, and that the quadrant diff
                    // is doing its job. Without them a Sentinel that had simply stopped ticking
                    // would satisfy the whole fixture.
                    helper.assertTrue(sentinel.sentinelCounters().sweeps() >= 2L,
                        "the Sentinel is genuinely awake and sweeping, so this is a refusal and "
                            + "not a fixture that asserted nothing; sweeps="
                            + sentinel.sentinelCounters().sweeps());
                    helper.assertTrue(sentinel.sentinelCounters().candidateVisits() >= 2L,
                        "the capped query genuinely returned this cell's two living entities, so "
                            + "the refusal above is a decision and not an empty quadrant; visits="
                            + sentinel.sentinelCounters().candidateVisits());
                    helper.assertTrue(sentinel.sentinelCounters().unchangedSweeps() >= 1L,
                        "an unchanged quadrant costs nothing beyond its visits; unchanged="
                            + sentinel.sentinelCounters().unchangedSweeps());
                    helper.assertValueEqual(sentinel.sentinelCounters().repelHits(), 0L,
                        "nothing unbound can ever be struck");
                    helper.assertTrue(sentinel.getTarget() == null,
                        "no permitted party is ever written to Mob.target");
                    helper.assertValueEqual(sentinel.sentinelState().strain(), 0,
                        "strain never rises on account of a party the Sentinel never bound");
                    helper.assertValueEqual(permitted.getHealth(), health,
                        "the permitted party takes no damage from the keeper at all");
                    helper.assertValueEqual(sentinel.phase(), Phase.VIGIL,
                        "the Sentinel stays at vigil rather than entering an episode");
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

    // ---------------------------------------------------------------- strain

    /**
     * Strain reaching its cap produces a seize and then a stand-down, and nothing else: no growth,
     * no rampage, no damage increase, no population and no death.
     */
    public static void ironboundSentinelStrainSeizesAndStandsDownWithoutRampage(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final IronboundSentinelEntity sentinel = spawnSentinel(fixture, new BlockPos(1, 1, 1));
            // One accrual short of the cap, so the first twenty-tick accrual while a subject is
            // held is what actually trips the seize inside this fixture's window.
            sentinel.setSentinelState(sentinel.sentinelState()
                .withStrain(IronboundSentinelRules.STRAIN_MAX - 1));
            final ServerPlayer intruder =
                fixture.connectedPlayer(INTRUDER, GameType.SURVIVAL);
            final java.util.concurrent.atomic.AtomicReference<Float> healthAtSeize =
                new java.util.concurrent.atomic.AtomicReference<>();

            // Both observations are deliberately expressed through monotone counters and the
            // settled end state rather than through the transient phase, because when the seize
            // starts depends on the entity's own UUID-seeded sweep and bearing staggers. A
            // phase==SEIZE assertion would only hold inside the forty-tick window that stagger
            // happens to place it in, which is exactly the shape that makes a fixture flaky.
            helper.runAfterDelay(BOUND_BY + IronboundSentinelRules.STRAIN_ACCRUAL_TICKS + 5L, () -> {
                try {
                    helper.assertTrue(sentinel.sentinelCounters().strainRises() >= 1L,
                        "holding a subject inside the ward is what raises strain; rises="
                            + sentinel.sentinelCounters().strainRises());
                    helper.assertValueEqual(sentinel.sentinelCounters().seizes(), 1L,
                        "the cap produces exactly one seize");
                    helper.assertTrue(sentinel.sentinelTransient().boundSubject().isEmpty(),
                        "the seize tears the episode down");
                    helper.assertTrue(sentinel.getTarget() == null,
                        "no queued strike survives the seize");
                    // Recorded here rather than at spawn: the sealed arena can cost a settling
                    // entity a point of contact damage on its first ticks, which has nothing to do
                    // with strain. What must hold is that the seize and the stand-down themselves
                    // cost nothing.
                    healthAtSeize.set(sentinel.getHealth());
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(300L, () -> {
                try {
                    helper.assertValueEqual(sentinel.sentinelState().charge(), Charge.INERT,
                        "the seize leads to a completed stand-down");
                    helper.assertValueEqual(sentinel.sentinelCounters().standDowns(), 1L,
                        "the stand-down completes exactly once");
                    helper.assertValueEqual(sentinel.sentinelState().strain(), 0,
                        "the ledger is cleared on the way into INERT and is not inherited");
                    helper.assertValueEqual(sentinel.phase(), Phase.STILLED,
                        "the seize ends in stillness, never in a rampage");
                    helper.assertTrue(sentinel.isAlive(),
                        "standing down is not a death: it drops nothing and despawns nothing");
                    helper.assertValueEqual(sentinel.getHealth(), healthAtSeize.get(),
                        "the seize and the stand-down cost the Sentinel no health at all");
                    helper.assertValueEqual(sentinel.getAttributeValue(Attributes.ATTACK_DAMAGE),
                        IronboundSentinelEntity.BASE_ATTACK_DAMAGE,
                        "strain never increases damage");
                    helper.assertValueEqual(sentinel.getAttributeValue(Attributes.MOVEMENT_SPEED),
                        IronboundSentinelEntity.BASE_MOVEMENT_SPEED,
                        "strain never increases speed");
                    helper.assertValueEqual(sentinel.sentinelCounters().seizes(), 1L,
                        "the seize does not repeat once the charge is drawn");
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

    // ---------------------------------------------------------------- hazards

    /**
     * A hazard preempts a running episode from any phase, tears it down before writing navigation
     * and keeps the station it already had.
     */
    public static void ironboundSentinelHazardPreemptsEpisodeAndKeepsItsStation(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final IronboundSentinelEntity sentinel = spawnSentinel(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer intruder =
                fixture.connectedPlayer(INTRUDER, GameType.SURVIVAL);
            final BlockPos expectedStation = helper.absolutePos(new BlockPos(1, 1, 1));

            helper.runAfterDelay(BOUND_BY, () -> {
                try {
                    helper.assertValueEqual(sentinel.sentinelCounters().bindings(), 1L,
                        "there is a live episode for the hazard to preempt");
                    sentinel.igniteForSeconds(6.0F);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(BOUND_BY + 5L, () -> {
                try {
                    helper.assertValueEqual(sentinel.phase(), Phase.EVADE,
                        "the hazard band takes the tick from the episode");
                    helper.assertValueEqual(sentinel.sentinelCounters().hazardInterruptions(), 1L,
                        "the interruption is counted exactly once per entry");
                    helper.assertTrue(sentinel.sentinelTransient().boundSubject().isEmpty(),
                        "the episode is torn down before hazard navigation is written");
                    helper.assertTrue(sentinel.getTarget() == null,
                        "no delayed hit survives the escape");
                    helper.assertValueEqual(sentinel.station().orElseThrow(), expectedStation,
                        "a Sentinel does not abandon its station because it caught fire");
                    helper.assertValueEqual(sentinel.sentinelState().charge(), Charge.CHARGED,
                        "the hazard band changes navigation, never the durable charge");
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

    // ---------------------------------------------------------------- lifecycle

    /**
     * The whole Zombie lifecycle is gone and the durable record survives a real unload and reload:
     * the station, bearing, strain and charge come back, the transient episode does not, and no
     * saved reference is resurrected into a delayed strike.
     */
    public static void ironboundSentinelSaveReloadAndZombieLifecycleAreReplaced(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final IronboundSentinelEntity sentinel = spawnSentinel(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer intruder =
                fixture.connectedPlayer(INTRUDER, GameType.SURVIVAL);

            helper.assertFalse(
                net.minecraft.world.entity.monster.zombie.Zombie.class.isInstance(sentinel),
                "the Sentinel is not a Zombie, so it has no Drowned conversion, no underwater "
                    + "timer, no baby form, no jockey and no reinforcement summoning");
            helper.assertFalse(ArcaneMob.class.isInstance(sentinel),
                "the Sentinel is not an ArcaneMob, so no generic tactical or ambient layer runs");
            helper.assertFalse(sentinel.canPickUpLoot(),
                "loot pickup is normalized off, so the Sentinel carries and drops no equipment");
            helper.assertValueEqual(sentinel.operationalTargetGoalCount(), 0,
                "the target selector is permanently empty");
            helper.assertTrue(sentinel.operationalGoalNames().size() == 3
                    && sentinel.operationalGoalNames().contains("FloatGoal")
                    && sentinel.operationalGoalNames().contains("LookAtPlayerGoal"),
                "only the three JUMP and LOOK goals are live on a spawned Sentinel; goals="
                    + sentinel.operationalGoalNames());
            helper.assertValueEqual(sentinel.getAttributeValue(Attributes.FOLLOW_RANGE),
                IronboundSentinelEntity.BASE_FOLLOW_RANGE,
                "the random follow-range spawn bonus is stripped, so every acquisition route "
                    + "produces byte-identical statistics");
            helper.assertValueEqual(sentinel.getAttributeValue(Attributes.MAX_HEALTH),
                IronboundSentinelEntity.BASE_MAX_HEALTH,
                "max health is the exact value already in effect, declared rather than invented");
            helper.assertValueEqual(
                sentinel.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE), 0.0D,
                "no wounded Sentinel can summon vanilla zombie reinforcements");
            for (final EquipmentSlot slot : EquipmentSlot.values()) {
                helper.assertTrue(sentinel.getItemBySlot(slot).isEmpty(),
                    "every equipment slot is normalized empty: " + slot);
            }

            helper.runAfterDelay(BOUND_BY, () -> {
                try {
                    helper.assertValueEqual(sentinel.sentinelCounters().bindings(), 1L,
                        "there is a live transient episode for the reload to discard");
                    sentinel.setSentinelState(sentinel.sentinelState().withBearing(3)
                        .withStrain(42));

                    final TagValueOutput output = TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
                    );
                    sentinel.saveWithoutId(output);
                    final var saved = output.buildResult().copy();
                    // The saved NBT carries the original's UUID, so the original has to leave the
                    // level before the copy enters it. Adding both makes the level reject the
                    // second outright, and a rejected entity never ticks, which would silently
                    // make every assertion below vacuous.
                    sentinel.discard();

                    final IronboundSentinelEntity reloaded = (IronboundSentinelEntity)
                        ModEntities.ALL.get("ironbound_sentinel").get()
                            .create(helper.getLevel(), EntitySpawnReason.LOAD);
                    helper.assertTrue(reloaded != null,
                        "the registered type must recreate saved state");
                    reloaded.load(TagValueInput.create(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved
                    ));
                    final BlockPos absolute = helper.absolutePos(new BlockPos(1, 1, 1));
                    reloaded.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
                    reloaded.setDeltaMovement(Vec3.ZERO);
                    final boolean entered = helper.getLevel().addFreshEntity(reloaded);
                    fixture.track(reloaded);
                    helper.assertTrue(entered,
                        "the reloaded Sentinel must actually enter the level: a rejected entity "
                            + "never ticks and would make every assertion below vacuous");

                    helper.assertValueEqual(reloaded.sentinelState().bearing(), 3,
                        "the bearing is durable, so the circuit of attention resumes where it was");
                    helper.assertValueEqual(reloaded.sentinelState().strain(), 42,
                        "the strain ledger is durable and is neither replayed nor subtracted");
                    helper.assertValueEqual(reloaded.sentinelState().station().orElseThrow(),
                        absolute, "the station is durable and is never re-derived on load");
                    helper.assertValueEqual(reloaded.sentinelState().charge(), Charge.CHARGED,
                        "the durable charge survives the reload on its settled arm");
                    helper.assertTrue(reloaded.sentinelTransient().boundSubject().isEmpty(),
                        "the bound subject is transient by design and cannot survive a reload");
                    helper.assertTrue(reloaded.getTarget() == null,
                        "a reload never resurrects a saved reference into a delayed strike");
                    helper.assertTrue(reloaded.sentinelTransient().attributedAttacker().isEmpty(),
                        "the attacker attribution is transient and cannot survive a reload either");
                    helper.assertValueEqual(reloaded.sentinelCounters().repelHits(), 0L,
                        "a reloaded Sentinel replays no hit");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(BOUND_BY + 30L, () -> {
                try {
                    final IronboundSentinelEntity reloaded = fixture.lastSentinel();
                    helper.assertTrue(reloaded.sentinelCounters().sweeps() >= 1L,
                        "the reloaded Sentinel requires new loaded perception and then resumes; "
                            + "sweeps=" + reloaded.sentinelCounters().sweeps());
                    // Deliberately a floor rather than an equality. Whether the resumed Sentinel
                    // has yet charged its first twenty-tick accrual depends on its own UUID-seeded
                    // sweep stagger; what the reload must never do is subtract the strain the
                    // unloaded gap did not spend.
                    helper.assertTrue(reloaded.sentinelState().strain() >= 42,
                        "resuming never subtracts missed strain for the unloaded gap; strain="
                            + reloaded.sentinelState().strain());
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

    /**
     * One deliberate open-handed act, driven through the real interaction path rather than through
     * the runtime helper, so the fixture proves the entity's own {@code mobInteract} reaches it.
     */
    private static void socket(final IronboundSentinelEntity sentinel, final ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        player.setPose(Pose.CROUCHING);
        final Vec3 toPlayer = player.position().subtract(sentinel.position());
        final float yaw = (float) (Math.atan2(toPlayer.z, toPlayer.x) * 180.0D / Math.PI) - 90.0F;
        sentinel.setYRot(yaw);
        sentinel.setYHeadRot(yaw);
        sentinel.setYBodyRot(yaw);
        sentinel.setXRot(0.0F);
        player.interactOn(sentinel, InteractionHand.MAIN_HAND, sentinel.position());
    }

    private static IronboundSentinelEntity spawnSentinel(
        final FixtureScope fixture,
        final BlockPos position
    ) {
        @SuppressWarnings("unchecked")
        final EntityType<IronboundSentinelEntity> type =
            (EntityType<IronboundSentinelEntity>) ModEntities.ALL.get("ironbound_sentinel").get();
        final IronboundSentinelEntity sentinel =
            fixture.spawn(type, position, EntitySpawnReason.EVENT);
        sentinel.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = fixture.helper.absolutePos(position);
        sentinel.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        return sentinel;
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

        /** The most recently tracked Sentinel, so a reload stage can be asserted on later. */
        private IronboundSentinelEntity lastSentinel() {
            for (int index = entities.size() - 1; index >= 0; index--) {
                if (entities.get(index) instanceof IronboundSentinelEntity sentinel) {
                    return sentinel;
                }
            }
            throw new IllegalStateException("no Sentinel was tracked by this fixture");
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
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.AIR));
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
            // mock player stays in ServerLevel.players() for the rest of the run and eats the
            // bounded candidate budget of every later acquisition sweep. This family was written
            // against 69d43b8, before that fix, so it releases rather than discards.
            entities.forEach(GameTestMockPlayers::release);
            entities.clear();
            for (int index = cleanupActions.size() - 1; index >= 0; index--) {
                cleanupActions.get(index).run();
            }
            cleanupActions.clear();
        }
    }
}
