package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.AmbientActivityProfile.ActivityType;
import com.kadamitas.warlockery.entity.UmbralSigilRules.Phase;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kadamitas.warlockery.entity.behavior.PhaseTimer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Six bounded live F22 fixtures. Every fixture asserts through spawned entities that keep their AI
 * enabled and tick themselves, cleans up every created entity in {@code finally}, and uses exact
 * state and counter assertions rather than elapsed-time guesses.
 *
 * <p><strong>Arena geometry.</strong> The framework seals the {@code warlockery:empty3x3x3} cell in a
 * barrier shell, so every entity and every derived destination has to stay inside relative 0..2
 * with entities at y=1 over a floor placed at y=0. The Sigil's seal radius is one block, so the
 * only snapshot centre whose three vertices all land inside the shell is the arena centre
 * {@code (1, 1, 1)}: every fixture that lets a seal run naturally therefore stands its subject
 * exactly there. A subject anywhere else would put a vertex outside the shell, where a flying
 * entity stalls silently while assertions read stale state.</p>
 *
 * <p>Every {@code runAfterDelay} and {@code onEachTick} is registered directly from the fixture
 * body and never from inside another such callback. Assertions read monotonic counters wherever a
 * seal may legitimately have advanced further by the time the callback runs.</p>
 *
 * <p>These fixtures depend on the coordinator-deferred ModEntities and ModGameTests wiring to route
 * {@code warlockery:umbral_sigil} through {@link UmbralSigilEntity} with a declared
 * {@code FLYING_SPEED} attribute, and to register these six functions.</p>
 */
public final class UmbralSigilGameTests {
    private static final BlockPos ARENA_CENTRE = new BlockPos(1, 1, 1);

    private UmbralSigilGameTests() {
    }

    /**
     * The signature behavior: a self-ticking Sigil appoints one visible survival player, derives
     * three vertices around it, flies to all three in order, and closes with exactly one ordinary
     * attributed melee attempt.
     */
    public static void umbralSigilTracesThreeVerticesAndStrikesOnce(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final UmbralSigilEntity sigil = spawnSigil(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer subject = fixture.connectedPlayer(ARENA_CENTRE);
            // LivingEntity clears lastHurtByMob a hundred ticks after the blow, so attribution is
            // latched as it happens rather than read at the end, where a null would mean an expiry
            // rather than an absence and the assertion would pass or fail for the wrong reason.
            final AtomicBoolean attributed = new AtomicBoolean();
            // A freshly joined mock player carries vanilla's join invulnerability window, and a
            // seal begun on the first sweep can close inside it, where the blow is refused for a
            // reason that has nothing to do with F22. The subject is therefore held ineligible for
            // the first hundred ticks, which both removes that window from the run and gives the
            // appointment filter a genuine ineligible candidate to reject.
            subject.setInvulnerable(true);
            helper.onEachTick(() -> {
                if (subject.getLastHurtByMob() == sigil) {
                    attributed.set(true);
                }
            });

            helper.runAfterDelay(100L, () -> {
                helper.assertTrue(sigil.sigilCounters().appointmentSweeps() >= 1L,
                    "sweeps genuinely ran against the ineligible candidate");
                helper.assertValueEqual(sigil.sigilCounters().sealsStarted(), 0L,
                    "an ineligible candidate is never appointed, however often it is examined");
                helper.assertTrue(sigil.sigilCounters().appointmentFailures() >= 1L,
                    "and a sweep that qualified nobody recorded its failure rather than retrying");
                helper.assertTrue(sigil.sigilCounters().appointmentCandidateVisits()
                        >= sigil.sigilCounters().appointmentSweeps(),
                    "the ineligible candidate was charged a read before it could be rejected");
                subject.setInvulnerable(false);
                subject.invulnerableTime = 0;
            });

            helper.runAfterDelay(260L, () -> {
                helper.assertTrue(sigil.tickCount > 0,
                    "the fixture subject is a genuinely self-ticking AI-enabled entity");
                helper.assertTrue(sigil.sigilCounters().appointmentSweeps() >= 1L,
                    "the dormant Sigil ran at least one bounded appointment sweep");
                helper.assertValueEqual(sigil.sigilCounters().sealsStarted(), 1L,
                    "exactly one seal was begun inside the cooldown window");
                // The far-vertex guard, live. A seal that stalled on the first vertex would report
                // one here, and a seal that skipped straight to the close would report none.
                helper.assertValueEqual(sigil.sigilCounters().verticesReached(),
                    (long) UmbralSigilRules.SEAL_VERTICES,
                    "all three vertices were genuinely reached, in order, by real flight");
                helper.assertValueEqual(sigil.sigilCounters().strikes(), 1L,
                    "the closed seal spends exactly one ordinary attributed melee attempt");
                helper.assertValueEqual(sigil.sigilCounters().strikesLanded(), 1L,
                    "the one attempt genuinely reached the world and was accepted");
                helper.assertTrue(attributed.get(),
                    "and it is attributed to this Sigil rather than to the world");
                helper.assertTrue(sigil.getTarget() == null,
                    "no live target survives the closed seal");
                // Every sweep is accounted for as an appointment or as a recorded failure, so a
                // sweep that qualified nobody can never have silently retried every tick.
                helper.assertTrue(
                    sigil.sigilCounters().appointmentFailures()
                        + sigil.sigilCounters().sealsStarted()
                        <= sigil.sigilCounters().appointmentSweeps(),
                    "every appointment sweep either appointed a subject or recorded its failure");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    /**
     * The counterplay. A subject that leaves the bounded encounter centre breaks an unfinished seal
     * before it can close, and the broken seal spends no attempt.
     *
     * <p>The natural seal this Sigil begins on its own closes in about eighty ticks, so the break
     * is applied to a seal opened at a known tick rather than raced against that window. Two armed
     * controls keep the fixture honest: the driven seal must be genuinely open before the break,
     * and it must still be open after the subject has stood still inside its centre for ten ticks,
     * which is what proves the boundary does not simply break by itself.</p>
     *
     * <p>The centre is re-anchored to the far corner rather than the subject walking out of it,
     * because the sealed shell is three blocks across and the declared boundary is two: no position
     * a subject can reach inside this arena is outside its own centre, and a fixture that could not
     * reach the boundary at all would be asserting nothing.</p>
     */
    public static void umbralSigilTargetEscapeBreaksUnfinishedSeal(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final UmbralSigilEntity sigil = spawnSigil(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer subject = fixture.connectedPlayer(ARENA_CENTRE);
            final AtomicLong strikesBeforeBreak = new AtomicLong(-1L);

            helper.runAfterDelay(140L, () -> {
                helper.assertTrue(sigil.sigilCounters().sealsStarted() >= 1L,
                    "a seal is genuinely begun by the Sigil itself before anything is driven");
                openSealOn(helper, sigil, subject);
                helper.assertTrue(sigil.sigilTransient().appointed(),
                    "armed control: the seal is genuinely open before the break");
                helper.assertTrue(UmbralSigilRules.sealing(sigil.sigilState().phase()),
                    "armed control: the open phase is a sealing phase, not a recovery");
            });

            helper.runAfterDelay(150L, () -> {
                helper.assertTrue(sigil.sigilTransient().appointed(),
                    "armed control: a subject standing inside its centre keeps the seal open");
                helper.assertTrue(UmbralSigilRules.sealing(sigil.sigilState().phase()),
                    "armed control: the seal is still being traced: "
                        + sigil.sigilState().phase());
                strikesBeforeBreak.set(sigil.sigilCounters().strikes());

                final BlockPos corner = helper.absolutePos(new BlockPos(2, 1, 2));
                subject.teleportTo(corner.getX() + 0.5D, corner.getY(), corner.getZ() + 0.5D);
                subject.setDeltaMovement(Vec3.ZERO);
                sigil.sigilTransient().seal = UmbralSigilRuntime.Seal.of(
                    subject.getUUID(),
                    UmbralSigilRuntime.dimensionOf(helper.getLevel()),
                    helper.absolutePos(new BlockPos(0, 1, 0))
                );
            });

            helper.runAfterDelay(158L, () -> {
                helper.assertFalse(sigil.sigilTransient().appointed(),
                    "a subject outside the bounded centre is released at once");
                helper.assertTrue(
                    sigil.sigilState().phase() == Phase.RECOVER
                        || sigil.sigilState().phase() == Phase.DORMANT,
                    "the broken seal cancels into recovery: " + sigil.sigilState().phase());
                helper.assertValueEqual(sigil.sigilCounters().strikes(),
                    strikesBeforeBreak.get(),
                    "a seal broken before its close spends no attempt of its own");
                helper.assertFalse(sigil.sigilState().struck(),
                    "and no spent-attempt latch survives the break");
                helper.assertTrue(sigil.getTarget() == null,
                    "a released Sigil holds no target");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    /**
     * The hazard branch and the damage branch are two different owners of the same ending, and this
     * fixture proves each of them separately, by the fact that distinguishes them.
     *
     * <p><strong>Which hazard reaches the hazard branch.</strong> The Sigil's declared hazard is any
     * block of the contact set inside its 3 x 3 x 3 neighbourhood, which is a proximity test rather
     * than a contact test: a flying Sigil breaks off a seal when a hazard is <em>next to</em> it,
     * before it is ever burnt. First execution showed that self-ignition is the degenerate case,
     * because fire deals its first point of damage on the same tick it is applied and the damage
     * hook then owns the ending a full cadence before the hazard sample can run. So the hazard
     * proved here is the non-degenerate one: an unlit campfire floor. {@code isHazardBlock} matches
     * on block identity, so an unlit campfire is a hazard by the Sigil's own rule, and
     * {@code CampfireBlock.entityInside} only damages when it is lit, so it cannot deal a point of
     * damage under any circumstance. The fixture asserts the Sigil was never on fire and never lost
     * a single point of health, which makes it impossible for the damage hook to have produced this
     * ending, and therefore impossible for this half to pass while the hazard branch is dead.</p>
     */
    public static void umbralSigilRouteHazardAndDamageCancel(final GameTestHelper helper) {
        buildUnlitCampfireFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final UmbralSigilEntity sigil = spawnSigil(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer subject = fixture.connectedPlayer(ARENA_CENTRE);
            final AtomicBoolean everOnFire = new AtomicBoolean();
            final AtomicBoolean everHurt = new AtomicBoolean();
            final AtomicLong interruptionsBeforeBlow = new AtomicLong(-1L);
            final AtomicLong strikesBeforeBlow = new AtomicLong(-1L);
            helper.onEachTick(() -> {
                if (sigil.isOnFire()) {
                    everOnFire.set(true);
                }
                if (sigil.getHealth() < sigil.getMaxHealth()) {
                    everHurt.set(true);
                }
            });

            helper.runAfterDelay(120L, () -> {
                helper.assertTrue(sigil.sigilCounters().sealsStarted() >= 1L,
                    "a seal genuinely began on its own before the hazard broke it");
                helper.assertTrue(sigil.sigilCounters().hazardSamples() >= 1L,
                    "the bounded hazard sample actually ran on the live tick");
                // THE BRANCH. Reached naturally, by proximity, with no drive of any kind.
                helper.assertTrue(sigil.sigilCounters().hazardInterruptions() >= 1L,
                    "an adjacent hazard preempts every sealing phase and is counted");
                helper.assertFalse(sigil.sigilTransient().appointed(),
                    "a preempted seal releases its subject rather than tracing beside a hazard");
                // The discriminator: no damage of any kind was available to end this seal, so the
                // ending can only have come from the hazard branch.
                helper.assertFalse(everOnFire.get(),
                    "the Sigil never caught fire, so no fire tick could have ended the seal");
                helper.assertFalse(everHurt.get(),
                    "the Sigil never lost a point of health, so the damage hook never ran");
                helper.assertTrue(sigil.sigilTransient().hazardActive(),
                    "and the sample that ended it genuinely observed the hazard");
                helper.assertTrue(
                    sigil.sigilCounters().blockReads()
                        >= sigil.sigilCounters().hazardSamples()
                            * UmbralSigilRules.MAX_HAZARD_READS,
                    "every hazard sample is charged its full neighbourhood, accepted or not");
                helper.assertTrue(
                    sigil.sigilCounters().routeFailures()
                        <= sigil.sigilCounters().navigationRequests()
                            + sigil.sigilCounters().unroutableRequests(),
                    "every counted failure is one of the requests that actually ran");

                // Clear the hazard so the second half can prove the other owner in isolation.
                buildFloor(helper);
            });

            helper.runAfterDelay(160L, () -> {
                helper.assertFalse(sigil.sigilTransient().hazardActive(),
                    "armed control: the hazard is genuinely gone before the blow half begins");
                helper.assertFalse(everHurt.get(),
                    "armed control: still no damage has reached the Sigil from the world");
                interruptionsBeforeBlow.set(sigil.sigilCounters().hazardInterruptions());
                strikesBeforeBlow.set(sigil.sigilCounters().strikes());

                // A struck Sigil abandons a half-drawn seal and never retaliates: the one attempt
                // it has belongs to its own close, not to a grudge.
                openSealOn(helper, sigil, subject);
                helper.assertTrue(sigil.sigilTransient().appointed(),
                    "armed control: the seal is genuinely open before the blow lands");
                sigil.invulnerableTime = 0;
                helper.assertTrue(sigil.hurtServer(
                        helper.getLevel(), helper.getLevel().damageSources().magic(), 2.0F),
                    "the fixture blow must genuinely land, or the cancel proves nothing");
            });

            helper.runAfterDelay(168L, () -> {
                helper.assertFalse(sigil.sigilTransient().appointed(),
                    "accepted damage cancels a half-drawn seal");
                helper.assertTrue(
                    sigil.sigilState().phase() == Phase.RECOVER
                        || sigil.sigilState().phase() == Phase.DORMANT,
                    "the cancelled seal is recovering: " + sigil.sigilState().phase());
                helper.assertTrue(sigil.getTarget() == null,
                    "a struck Sigil gains no target and holds no grudge");
                // The discriminator for this half: the hazard branch did not fire, so this ending
                // is owned by the damage hook and by nothing else.
                helper.assertValueEqual(sigil.sigilCounters().hazardInterruptions(),
                    interruptionsBeforeBlow.get(),
                    "no hazard interruption was involved in the damage ending");
                helper.assertValueEqual(sigil.sigilCounters().strikes(),
                    strikesBeforeBlow.get(),
                    "no cancelled seal ever spends an attempt");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    /**
     * A dense scene stays inside every declared bound: the candidate cap, the charged read ceiling,
     * the per-level path-start quota, and a stable, identical choice of subject across Sigils.
     */
    public static void umbralSigilDenseCandidatesStayCappedAndStable(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final List<UmbralSigilEntity> sigils = new ArrayList<>();
            for (final BlockPos position : List.of(
                new BlockPos(0, 1, 0), new BlockPos(2, 1, 0), new BlockPos(0, 1, 2),
                new BlockPos(2, 1, 2), new BlockPos(0, 2, 1), new BlockPos(2, 2, 1)
            )) {
                sigils.add(spawnSigil(fixture, position));
            }
            final ServerPlayer only = fixture.connectedPlayer(ARENA_CENTRE);
            final AtomicLong observedTicks = new AtomicLong();
            helper.onEachTick(() -> {
                observedTicks.incrementAndGet();
                // Fixture life support: six Sigils can spend six attempts and this fixture is about
                // bounds rather than lethality, so the one eligible subject is kept alive and
                // therefore kept eligible for the whole run.
                if (only.getHealth() < only.getMaxHealth()) {
                    only.setHealth(only.getMaxHealth());
                }
            });

            helper.runAfterDelay(200L, () -> {
                long sweeps = 0;
                long visits = 0;
                long reads = 0;
                long navigationRequests = 0;
                long appointed = 0;
                for (final UmbralSigilEntity sigil : sigils) {
                    helper.assertTrue(sigil.tickCount > 0, "every subject ticks itself");
                    sweeps += sigil.sigilCounters().appointmentSweeps();
                    visits += sigil.sigilCounters().appointmentCandidateVisits();
                    reads += sigil.sigilCounters().appointmentReads();
                    navigationRequests += sigil.sigilCounters().navigationRequests();
                    appointed += sigil.sigilCounters().sealsStarted();
                }
                helper.assertTrue(sweeps >= (long) sigils.size(),
                    "every Sigil ran at least one sweep, so none of these bounds is vacuous");
                helper.assertTrue(navigationRequests >= 1L,
                    "real paths were genuinely built, so the quota bound is not vacuous");
                helper.assertTrue(appointed >= 1L,
                    "the single loaded survival player was genuinely appointed");
                helper.assertTrue(visits >= sweeps,
                    "every sweep charged at least the one candidate it had to look at");
                helper.assertTrue(visits <= sweeps * UmbralSigilRules.MAX_PLAYER_CANDIDATES,
                    "no sweep exceeded its declared candidate cap");
                helper.assertTrue(reads >= visits,
                    "every candidate visit was charged before it could be filtered");
                helper.assertTrue(reads <= sweeps * UmbralSigilRules.MAX_APPOINTMENT_READS,
                    "no sweep exceeded its declared charged-read ceiling");
                // The per-level quota: however many Sigils want to move, one level may only start
                // a fixed number of paths per tick, so the whole scene's real path builds are
                // bounded by the ticks it has been alive for.
                helper.assertTrue(
                    navigationRequests
                        <= observedTicks.get() * UmbralSigilRules.MAX_PATH_STARTS_PER_LEVEL_TICK,
                    "the per-level path-start quota bounded the whole dense scene: "
                        + navigationRequests + " over " + observedTicks.get() + " ticks");
                // Stability: there is exactly one eligible player, so every Sigil that appointed
                // anybody appointed the same one, with no dependence on iteration order.
                for (final UmbralSigilEntity sigil : sigils) {
                    if (sigil.sigilTransient().appointed()) {
                        helper.assertTrue(UmbralSigilRuntime.isSubject(sigil, only.getUUID()),
                            "a dense scene never appoints a crowd or an unstable subject");
                    }
                }
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    /** A reload never resumes a seal, never closes one, and never hands back a spent attempt. */
    public static void umbralSigilReloadNeverReplaysCloseOrStrike(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        final java.util.concurrent.atomic.AtomicReference<UmbralSigilEntity> copy =
            new java.util.concurrent.atomic.AtomicReference<>();
        try {
            final UmbralSigilEntity sigil = spawnSigil(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer subject = fixture.connectedPlayer(ARENA_CENTRE);

            helper.runAfterDelay(140L, () -> {
                // Drive the exact state a reload must never resume: an open strike window whose one
                // attempt has already been spent.
                openSealOn(helper, sigil, subject);
                sigil.setSigilState(sigil.sigilState()
                    .withTimer(PhaseTimer.start(Phase.STRIKE, UmbralSigilRules.STRIKE_TICKS))
                    .withStrikes(1));
                helper.assertValueEqual(sigil.sigilState().phase(), Phase.STRIKE,
                    "armed control: the driven state is genuinely inside the strike window");
                helper.assertTrue(sigil.sigilState().struck(),
                    "armed control: the driven state genuinely carries a spent attempt");
                helper.assertTrue(sigil.sigilTransient().appointed(),
                    "armed control: and it is genuinely sealing somebody");

                // The saved tag carries the original UUID, so the original is discarded before the
                // copy exists rather than after.
                final UmbralSigilEntity reloaded = reload(helper, fixture, sigil);
                copy.set(reloaded);

                helper.assertValueEqual(reloaded.sigilState().phase(), Phase.RECOVER,
                    "a reloaded Sigil enters recovery and can neither trace nor close");
                helper.assertValueEqual(reloaded.sigilState().remainingTicks(),
                    UmbralSigilRules.RECOVER_TICKS,
                    "the recovery it enters is a whole fresh one");
                helper.assertTrue(reloaded.sigilState().struck(),
                    "the spent attempt survives, so the reload grants no second one");
                helper.assertValueEqual(reloaded.sigilCounters().strikes(), 0L,
                    "a reload replays no strike of its own");
                helper.assertFalse(reloaded.sigilTransient().appointed(),
                    "a reloaded Sigil is sealing nobody: no subject reference is ever persisted");
                helper.assertTrue(reloaded.getTarget() == null,
                    "no live target survives a load");
                helper.assertFalse(
                    reloaded.sigilState().write().getStringOr("Phase", "").contains("strike"),
                    "and the shape it would save again is not a strike either");
            });

            helper.runAfterDelay(200L, () -> {
                helper.assertTrue(copy.get() != null, "the reload stage genuinely ran");
                helper.assertValueEqual(copy.get().sigilCounters().strikes(), 0L,
                    "and no strike arrived from the reloaded copy in the ticks after the load");
                helper.assertFalse(copy.get().sigilTransient().appointed(),
                    "and it never re-appointed the subject it was saved sealing");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    /**
     * The Sigil is its own being and changes nothing outside itself: no other family's identity, no
     * Sanctity ward, no block, no player state, and no surviving generic ambient row.
     */
    public static void umbralSigilFamiliesWardsAndWorldStayIsolated(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final UmbralSigilEntity sigil = spawnSigil(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer subject = fixture.connectedPlayer(ARENA_CENTRE);
            final Map<BlockPos, BlockState> before = snapshot(helper);
            helper.assertTrue(before.size() == 27, "the whole arena cell is snapshotted");

            helper.runAfterDelay(200L, () -> {
                helper.assertTrue(sigil.tickCount > 0,
                    "the fixture subject is a genuinely self-ticking AI-enabled entity");
                helper.assertTrue(sigil.sigilCounters().sealsStarted() >= 1L,
                    "the Sigil genuinely did its own work during this run");

                // No foreign class identity, and the shipped hostile identity is kept.
                helper.assertFalse(Vex.class.isInstance(sigil),
                    "the Sigil is no longer a Vex copy and no longer noclips");
                helper.assertFalse(SpiritMob.class.isInstance(sigil),
                    "the Sigil carries no shared SpiritMob class identity");
                helper.assertFalse(ArcaneMob.class.isInstance(sigil),
                    "the Sigil carries no shared ArcaneMob class identity");
                helper.assertFalse(SpectralEntity.class.isInstance(sigil),
                    "the Sigil does not borrow the F19 spectral body");
                helper.assertFalse(EchoShadeEntity.class.isInstance(sigil),
                    "the Sigil is not an Echo Shade");
                helper.assertFalse(SpectreEntity.class.isInstance(sigil),
                    "the Sigil is not a Spectre");
                helper.assertFalse(PoltergeistEntity.class.isInstance(sigil),
                    "the Sigil is not a Poltergeist");
                helper.assertTrue(Monster.class.isInstance(sigil),
                    "the Sigil keeps its shipped hostile monster identity");
                helper.assertTrue(sigil.isNoGravity(), "and keeps its flying spectral body");
                helper.assertValueEqual(sigil.creatureKind(),
                    ArcaneCreature.CreatureKind.UMBRAL_SIGIL, "and its exact registry kind");

                // Movement and attack authority belong to the runtime alone.
                helper.assertValueEqual(sigil.operationalTargetGoalCount(), 0,
                    "no target goal is ever registered, so no charge goal can return");
                helper.assertTrue(sigil.operationalGoalNames().stream().allMatch(name ->
                        "LookAtPlayerGoal".equals(name) || "LookOnlyRandomLookGoal".equals(name)),
                    "only look goals remain: " + sigil.operationalGoalNames());

                // No neighbour's payload. The Sigil applies no effect of any kind.
                helper.assertFalse(subject.hasEffect(MobEffects.DARKNESS),
                    "the Sigil never borrows Spectre dread");
                helper.assertFalse(subject.hasEffect(MobEffects.WEAKNESS),
                    "the Sigil never borrows Spectre weakness");
                helper.assertFalse(subject.hasEffect(MobEffects.BLINDNESS),
                    "the Sigil applies no status effect at all");
                helper.assertTrue(subject.getActiveEffects().isEmpty(),
                    "no status effect of any kind reached the subject");
                for (final EquipmentSlot slot : EquipmentSlot.values()) {
                    helper.assertTrue(sigil.getItemBySlot(slot).isEmpty(),
                        "the Sigil carries and copies nothing: " + slot);
                }

                // No ward, no block edit, nothing placed or removed anywhere in the cell. This is
                // what keeps the name collision with the DREAD_SIGIL Sanctity ward a collision.
                final Map<BlockPos, BlockState> after = snapshot(helper);
                helper.assertTrue(after.size() == before.size(),
                    "the same cell is compared before and after");
                for (final Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
                    helper.assertTrue(entry.getValue() == after.get(entry.getKey()),
                        "no block in the arena was placed, removed or changed: " + entry.getKey());
                }
                helper.assertFalse(before.containsValue(Blocks.SOUL_LANTERN.defaultBlockState()),
                    "the arena never contained a soul light, so no vigil could have run here");

                // The retired generic row. This is the whole of the F22 shared-file change and it
                // had to be the whole row, because the profile constructor rejects an empty set.
                helper.assertTrue(
                    AmbientActivityProfile.forKind(
                        ArcaneCreature.CreatureKind.UMBRAL_SIGIL).isEmpty(),
                    "the Sigil is delegated off the generic ambient dispatch");
                helper.assertTrue(
                    AmbientActivityProfile.forType(ActivityType.SOUL_LANTERN_VIGIL) == null,
                    "and the whole soul-lantern vigil row is retired rather than emptied");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- fixture support

    /**
     * Opens a seal on this exact subject at a known tick. The natural seal a Sigil begins on its
     * own closes in about eighty ticks, so a fixture that has to act on an <em>open</em> seal opens
     * one deliberately rather than racing that window; the phase machine, the ending rules and the
     * strike gate the fixture is actually testing all run on the live tick either way.
     */
    private static void openSealOn(
        final GameTestHelper helper,
        final UmbralSigilEntity sigil,
        final ServerPlayer subject
    ) {
        sigil.setSigilState(sigil.sigilState().withCooldown(0).startSeal());
        sigil.sigilTransient().seal = UmbralSigilRuntime.Seal.of(
            subject.getUUID(),
            UmbralSigilRuntime.dimensionOf(helper.getLevel()),
            helper.absolutePos(ARENA_CENTRE)
        );
    }

    private static void buildFloor(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 0, 2))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
    }

    /**
     * A floor of unlit campfires: a hazard by {@code isHazardBlock}, which matches on block
     * identity, and provably harmless, because {@code CampfireBlock} damages only when lit. It is
     * the only way to put the Sigil next to a declared hazard without giving the damage hook a
     * chance to own the ending first.
     */
    private static void buildUnlitCampfireFloor(final GameTestHelper helper) {
        final BlockState unlit =
            Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false);
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 0, 2))
            .forEach(position -> helper.setBlock(position, unlit));
    }

    private static Map<BlockPos, BlockState> snapshot(final GameTestHelper helper) {
        final Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 2, 2))
            .forEach(position -> states.put(position.immutable(), helper.getBlockState(position)));
        return states;
    }

    private static UmbralSigilEntity reload(
        final GameTestHelper helper,
        final FixtureScope fixture,
        final UmbralSigilEntity original
    ) {
        final TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        original.saveWithoutId(output);
        final var saved = output.buildResult().copy();
        original.discard();
        final Entity restored = ModEntities.ALL.get("umbral_sigil").get()
            .create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(restored instanceof UmbralSigilEntity,
            "the registered umbral_sigil type must recreate a dedicated Sigil");
        fixture.track(restored);
        restored.load(TagValueInput.create(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        return (UmbralSigilEntity) restored;
    }

    private static UmbralSigilEntity spawnSigil(
        final FixtureScope fixture,
        final BlockPos position
    ) {
        @SuppressWarnings("unchecked")
        final EntityType<UmbralSigilEntity> type =
            (EntityType<UmbralSigilEntity>) ModEntities.ALL.get("umbral_sigil").get();
        return placed(fixture, fixture.spawn(type, position), position);
    }

    /**
     * Places a subject without ever disabling its AI. Every F22 fixture asserts through an entity
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
            entities.forEach(Entity::discard);
            entities.clear();
        }
    }
}
