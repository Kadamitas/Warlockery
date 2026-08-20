package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Action;
import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Phase;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Six bounded live F24 fixtures for {@code warlockery:spectral_familiar}.
 *
 * <p>They assert through directly constructed, added, self-ticking bodies and depend on the
 * coordinator-deferred {@code ModEntities} and {@code ModGameTests} wiring to route
 * {@code warlockery:spectral_familiar} through {@link SpectralFamiliarEntity} and to register these
 * functions.</p>
 *
 * <p>Every fixture that reads blocks wider than the arena runs in
 * {@code warlockery:spectral_familiar_isolated} and carries an armed contamination control: the
 * survey assertion pins the <em>exact</em> claimed guide position, so an ore belonging to a
 * neighbouring arena would move the claim and fail loudly rather than pass silently. Every relative
 * coordinate stays inside the 3x3x3 arena, so nothing depends on a chunk that is loaded but not
 * ticked.</p>
 */
public final class SpectralFamiliarGameTests {

    private SpectralFamiliarGameTests() {
    }

    private static final Identifier IRON_ORE = Identifier.parse("minecraft:iron_ore");

    public static void spectralFamiliarSurveysSampleAndReturns(final GameTestHelper helper) {
        surveyAndSignal(helper);
    }

    public static void spectralFamiliarOwnerDefenseInterruptsThenReturns(final GameTestHelper helper) {
        defenseIsolationAndReload(helper);
    }

    public static void spectralFamiliarScanAndRouteCapsHold(final GameTestHelper helper) {
        surveyAndSignal(helper);
    }

    public static void spectralFamiliarReloadDoesNotReplaySignal(final GameTestHelper helper) {
        defenseIsolationAndReload(helper);
    }

    public static void spectralFamiliarTwoPlayerOwnershipIsolated(final GameTestHelper helper) {
        defenseIsolationAndReload(helper);
    }

    public static void spectralFamiliarNeighborsAndWorldStayUntouched(final GameTestHelper helper) {
        spiritBodyWithoutGenericWriters(helper);
    }

    // =================================================================================
    // F1: one spirit body, one controller, and no generic writer anywhere near it
    // =================================================================================

    private static void spiritBodyWithoutGenericWriters(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final SpectralFamiliarEntity familiar = fixture.spawnFamiliar(new BlockPos(1, 1, 1));

            helper.assertValueEqual(familiar.getClass().getName(),
                SpectralFamiliarEntity.class.getName(),
                "the exact registered spectral_familiar must construct the dedicated body");
            helper.assertValueEqual(familiar.creatureKind(), CreatureKind.FAMILIAR, "exact kind");

            // The structural fact the whole family hangs off. CreatureVisualProfile gives FAMILIAR
            // the SPIRIT archetype, so createArcaneType routes it through createSpirit and it is a
            // Vex. Any cast or instanceof against ArcaneMob would throw, which is why the three
            // animal familiars' BODY could not be shared even though their RULES are.
            helper.assertTrue(familiar instanceof SpiritMob, "a spectral familiar is a SpiritMob");
            helper.assertTrue(familiar instanceof Vex, "and therefore a Vex");
            // Written reflectively because the direct form does not compile: javac rejects
            // "familiar instanceof ArcaneMob" outright as provably impossible, which is a stronger
            // statement of the same fact than any runtime assertion could be, and it is exactly why
            // AnimalFamiliarMob could not have been this body's base class.
            helper.assertFalse(ArcaneMob.class.isInstance(familiar),
                "a spectral familiar is never an ArcaneMob, so it is never a Zombie either");
            helper.assertFalse(ArcaneMob.class.isAssignableFrom(SpectralFamiliarEntity.class),
                "and the two hierarchies do not meet below SpiritMob");

            // Vex.registerGoals installs a charge attack, a random move, a hurt-by target and a
            // NearestAttackableTargetGoal<Player>. That last one makes an idle bound familiar hunt
            // whichever player wanders closest. registerGoals is overridden so none is installed.
            final List<String> goals = familiar.operationalGoalNames();
            helper.assertFalse(goals.contains("VexChargeAttackGoal"),
                "the Vex charge attack is a second movement writer and must not exist");
            helper.assertFalse(goals.contains("VexRandomMoveGoal"),
                "the Vex random drift is a second movement writer and must not exist");
            helper.assertTrue(goals.contains("FloatGoal"), "float stays: it is presentation");
            helper.assertTrue(goals.contains("LookAtPlayerGoal"), "look stays: it is presentation");
            helper.assertValueEqual(familiar.operationalTargetGoalCount(), 0,
                "no target-selector goal at all: this runtime is the sole target authority");

            // The peaceful lifecycle hazard, stated as the two ingredients that make it real.
            // Mob.checkDespawn discards on !isAllowedInPeaceful BEFORE it consults persistence, so
            // without the checkDespawn override a bound familiar is deleted by a difficulty change.
            helper.assertFalse(familiar.getType().isAllowedInPeaceful(),
                "the registered type really is swept by the peaceful branch of checkDespawn");
            helper.assertFalse(familiar.isPersistenceRequired(),
                "an unbound familiar is not persistent and is not exempted from anything");

            final List<BlockState> arena = fixture.snapshotArena();

            helper.runAfterDelay(40L, () -> {
                try {
                    helper.assertTrue(familiar.spectralCounters().decisions() > 0L,
                        "the body must actually reach its own tick");
                    helper.assertTrue(familiar.spectralCounters().genericLayersDeclined() > 0L,
                        "and reach and decline the generic profiled, tactical and ambient layers");
                    fixture.assertArenaUnedited(arena,
                        "a spectral familiar never places, breaks or marks a block in any phase");
                    helper.assertValueEqual(familiar.spectralCounters().auraPulses(), 0L,
                        "an unbound familiar grants no Haste to anybody");
                    helper.assertValueEqual(familiar.spectralCounters().surveys(), 0L,
                        "and never surveys, because there is no owner to guide");
                    helper.assertValueEqual(familiar.lastSpectralDecision().action(), Action.IDLE,
                        "an unbound familiar sits on the idle rung and writes no movement");
                    helper.assertTrue(familiar.isNoGravity(),
                        "the chassis hovers: Vex.tick sets no gravity every tick");
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

    // =================================================================================
    // F2: the inherited persistence latch, both halves, through the real binding path
    // =================================================================================

    private static void bindingAndPersistenceLatch(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            // Directly constructed and added rather than spawned through GameTestEntityBuilder,
            // which latches persistence on test-spawned mobs and would make the first assertion
            // vacuously false.
            final SpectralFamiliarEntity nameTagged = fixture.spawnFamiliar(new BlockPos(0, 1, 0));
            final SpectralFamiliarEntity bound = fixture.spawnFamiliar(new BlockPos(2, 1, 2));

            helper.assertFalse(nameTagged.vanillaPersistenceLatched(),
                "a freshly constructed familiar carries no vanilla latch");
            helper.assertFalse(bound.vanillaPersistenceLatched(),
                "and neither does the one about to be bound");
            helper.assertFalse(nameTagged.contractLatchSuppressed(),
                "the suppression window is closed outside mobInteract");

            // HALF ONE: every ordinary latch is honoured. This is the name tag, the dispenser, the
            // hopper and /item replace entity. The F10 remedy that overrode isPersistenceRequired
            // to a derived predicate was REJECTED in review precisely because it dropped this one.
            nameTagged.setPersistenceRequired();
            helper.assertTrue(nameTagged.vanillaPersistenceLatched(),
                "an ordinary setPersistenceRequired must latch exactly as vanilla does");
            helper.assertTrue(nameTagged.isPersistenceRequired(),
                "so it is persistent for a reason this family did not invent");

            // HALF TWO: the one write made inside the contract binding is refused. The path is the
            // real one: interactOn -> SpiritMob.mobInteract -> CreatureBehavior.interact ->
            // CreatureBehaviorRuntime.interact -> case FAMILIAR -> interactSpectralFamiliar ->
            // bindCompanion -> finishBinding -> setPersistenceRequired.
            final ServerPlayer player = fixture.connectedPlayer(new BlockPos(1, 1, 1));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STRING, 3));
            player.interactOn(bound, InteractionHand.MAIN_HAND, Vec3.ZERO);

            helper.assertTrue(CreatureBehaviorState.isOwnedBy(bound, player.getUUID()),
                "the binder really did bind through the frozen acquisition path");
            helper.assertValueEqual(
                player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 2,
                "and consumed exactly one binder");
            helper.assertFalse(bound.vanillaPersistenceLatched(),
                "but the unclearable contract latch was refused, so unbinding can still release it");
            helper.assertFalse(bound.contractLatchSuppressed(),
                "and the finally arm closed the window again");
            helper.assertTrue(bound.isPersistenceRequired(),
                "a bound familiar persists, for a reason that is clearable");

            CreatureBehaviorState.unbind(bound);
            helper.assertFalse(bound.isPersistenceRequired(),
                "and an unbound one goes back to ordinary despawn, which the one-way latch forbids");
            helper.assertTrue(nameTagged.isPersistenceRequired(),
                "the separately latched familiar is untouched throughout");

            final List<BlockState> arena = fixture.snapshotArena();

            helper.runAfterDelay(30L, () -> {
                try {
                    helper.assertTrue(bound.spectralCounters().decisions() > 0L,
                        "both bodies keep ticking through the whole interaction");
                    fixture.assertArenaUnedited(arena, "binding edits no blocks");
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

    // =================================================================================
    // F3: one bounded survey, one episode, one signal, and a world that stays untouched
    // =================================================================================

    private static void surveyAndSignal(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.floor();
            // One ore, at an exact position inside the arena. Pinning the exact claimed position is
            // the armed contamination control: an ore in a neighbouring arena would move the claim.
            //
            // Its offset from the familiar is (-1, -1, -1), squared distance THREE, and that is the
            // point of it rather than an arbitrary corner. ScanEnvelope's window is a fixed near
            // anchor of readCap/2 = 32 centre-out offsets plus a rotating page over the far tail;
            // the anchor at this cap holds every offset out to squared distance three, so this ore
            // is read by every single survey. It is nevertheless outside the first TWELVE offsets,
            // which is what an earlier form of the survey loop could reach: that form made every
            // loaded position a candidate, so the twelve-candidate cap filled from the innermost
            // offsets on every survey and this ore was charged, counted and never inspected. This
            // fixture failed with "opens exactly one episode ... was 0" against that form, which is
            // what makes the assertion below a live regression test for the innermost-ring defect
            // rather than a restatement of the geometry.
            final BlockPos orePosition = new BlockPos(0, 0, 0);
            fixture.placeBlock(orePosition, Blocks.IRON_ORE);
            final BlockPos absoluteOre = helper.absolutePos(orePosition);

            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(0, 1, 0));
            final SpectralFamiliarEntity guide = fixture.spawnFamiliar(new BlockPos(1, 1, 1));
            final SpectralFamiliarEntity unsampled = fixture.spawnFamiliar(new BlockPos(2, 2, 2));
            CreatureBehaviorState.bind(guide, owner.getUUID());
            CreatureBehaviorState.bind(unsampled, owner.getUUID());
            CreatureBehaviorState.setSampleBlock(guide, IRON_ORE);
            // The control familiar is bound but never sampled: same owner, same arena, no episode.

            guide.makeSurveyDue();
            unsampled.makeSurveyDue();

            final List<BlockState> arena = fixture.snapshotArena();

            helper.runAfterDelay(10L, () -> {
                try {
                    helper.assertTrue(guide.spectralCounters().surveys() > 0L,
                        "the bounded survey must actually run");
                    helper.assertTrue(guide.spectralCounters().surveyBlockReads() > 0L,
                        "and must actually read blocks");
                    helper.assertTrue(
                        guide.spectralCounters().surveyBlockReads()
                            <= (long) SpectralFamiliarRules.SURVEY_READ_CAP
                                * guide.spectralCounters().surveys()
                                + guide.spectralCounters().episodesOpened(),
                        "no survey may exceed its read cap, plus the one arrival revalidation");
                    helper.assertTrue(
                        guide.spectralCounters().surveyCandidatesInspected()
                            <= (long) SpectralFamiliarRules.SURVEY_CANDIDATE_CAP
                                * guide.spectralCounters().surveys(),
                        "and no survey may inspect more than twelve candidates");
                    // The matching lower bound. Without it the cap above is satisfied by a survey
                    // that inspected nothing at all, which is the same shape of unfailable
                    // assertion as a counter with no increment site: an upper bound alone is
                    // satisfied by zero. Each survey reads this arena's one matching ore, so each
                    // one inspects at least one candidate.
                    helper.assertTrue(
                        guide.spectralCounters().surveyCandidatesInspected()
                            >= guide.spectralCounters().surveys(),
                        "and it must really inspect candidates rather than satisfying the cap with "
                            + "zero, which an upper bound alone always does");

                    helper.assertValueEqual(guide.spectralCounters().episodesOpened(), 1L,
                        "one qualifying survey opens exactly one episode");
                    helper.assertValueEqual(guide.spectralState().guideBlock(),
                        java.util.Optional.of(absoluteOre),
                        "and it remembers the EXACT ore it found, not merely some position");
                    helper.assertTrue(guide.spectralState().episodeRunning(),
                        "the opened episode is still in flight at this delay");
                    helper.assertTrue(guide.spectralState().episodeSample().isPresent(),
                        "and it froze the sample identity it opened with, so a mid-flight re-sample "
                            + "invalidates it rather than silently re-aiming it");
                    // Deliberately NOT asserting Phase.APPROACH here. Every offset in this
                    // envelope's fixed near anchor is within ARRIVAL_DISTANCE_SQUARED of the
                    // familiar, so inside a 3x3x3 arena the approach leg is structurally one tick
                    // long and the phase has already advanced to SIGNAL by any observable delay.
                    // Asserting the exact phase here pins a decaying value, which is the risk the
                    // register calls out.

                    // The control. Same owner, same arena, same ore, no sample.
                    helper.assertValueEqual(unsampled.spectralCounters().surveys(), 0L,
                        "a bound but unsampled familiar never surveys");
                    helper.assertValueEqual(unsampled.spectralCounters().episodesOpened(), 0L,
                        "and opens no episode");
                    helper.assertValueEqual(unsampled.lastSpectralDecision().action(), Action.HOVER,
                        "it hovers with its owner instead");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            // Past the twenty-tick signal phase and well inside the two-hundred-tick approach and
            // return deadlines, so the episode has had time to run but cannot have expired twice.
            helper.runAfterDelay(150L, () -> {
                try {
                    helper.assertValueEqual(guide.spectralCounters().signalsEmitted(), 1L,
                        "the episode signalled exactly once, and an episode can never signal twice");
                    helper.assertValueEqual(guide.spectralCounters().episodesCompleted(), 1L,
                        "the full cycle ran: survey, approach, signal, return, cooldown");
                    helper.assertValueEqual(guide.spectralCounters().episodesAbandoned(), 0L,
                        "and nothing was abandoned along the way");
                    helper.assertValueEqual(guide.spectralCounters().surveys(), 1L,
                        "the six-hundred-tick guide cooldown really does hold off a second survey");
                    helper.assertValueEqual(guide.spectralState().phase(), Phase.DORMANT,
                        "and the familiar is back to dormant rather than stuck mid-episode");
                    helper.assertFalse(guide.spectralState().guideReady(
                        helper.getLevel().getGameTime()),
                        "with its cooldown open");
                    helper.assertTrue(guide.spectralCounters().driftRequests() > 0L,
                        "the approach really steers the move control");
                    helper.assertTrue(guide.spectralCounters().auraPulses() > 0L,
                        "and the frozen Haste aura keeps pulsing for its loaded owner throughout");
                    // The world is exactly as the fixture left it: nothing was mined or marked, by
                    // either body. Both familiars ticked in this one arena for a hundred and fifty
                    // ticks, so one comparison of its real block states covers both of them.
                    fixture.assertArenaUnedited(arena,
                        "surveying, approaching and signalling never place, break or mark a block");
                    helper.assertBlockPresent(Blocks.IRON_ORE, orePosition);
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

    // =================================================================================
    // F4: one defensive lease, two owners stay isolated, and a reload replays nothing
    // =================================================================================

    private static void defenseIsolationAndReload(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.floor();
            final SpectralFamiliarEntity mine = fixture.spawnFamiliar(new BlockPos(0, 1, 0));
            final SpectralFamiliarEntity theirs = fixture.spawnFamiliar(new BlockPos(2, 1, 0));
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(1, 1, 0));
            CreatureBehaviorState.bind(mine, owner.getUUID());
            CreatureBehaviorState.bind(theirs, UUID.randomUUID());

            final Aggressor aggressor = fixture.spawnAggressor(new BlockPos(2, 1, 2));
            owner.setLastHurtByMob(aggressor);

            final List<BlockState> arena = fixture.snapshotArena();

            helper.runAfterDelay(5L, () -> {
                try {
                    helper.assertValueEqual(mine.spectralCounters().defenceLeases(), 1L,
                        "a direct legal attack on the owner yields exactly one defensive lease");
                    helper.assertValueEqual(mine.lastSpectralDecision().action(),
                        Action.DEFEND_OWNER, "defence outranks every routine rung");
                    helper.assertTrue(mine.spectralState().defenceTargetId().isPresent(),
                        "the lease names its one attacker");

                    // Two-owner isolation, which is the same shape as the brewing gate's defect.
                    helper.assertValueEqual(theirs.spectralCounters().defenceLeases(), 0L,
                        "another player's familiar never takes a lease from this owner's attacker");
                    helper.assertTrue(theirs.getTarget() == null,
                        "and never acquires a target from it either");
                    helper.assertValueEqual(theirs.spectralCounters().auraPulses(), 0L,
                        "nor does it pulse Haste for an owner that is not loaded for it");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(60L, () -> {
                try {
                    helper.assertValueEqual(mine.spectralCounters().defenceLeases(), 1L,
                        "one attack event yields one lease and never a stream of them");
                    // EXACTLY one, from both sides. The upper bound alone was satisfied by zero,
                    // so the fix it was meant to pin - one intercept means one opportunity, because
                    // completion spends the opportunity and releases the lease in the same tick -
                    // was not pinned from below at all: a familiar that never reached its attacker
                    // passed it just as happily as one that reached it once.
                    helper.assertValueEqual(mine.spectralCounters().meleeOpportunities(), 1L,
                        "the familiar really did close on its attacker, and a hundred-tick lease "
                            + "spent beside it bought exactly one melee opportunity, not a chain");
                    helper.assertTrue(mine.spectralState().defenceTargetId().isEmpty(),
                        "and completing the intercept released the lease in the same tick");
                    helper.assertFalse(
                        mine.spectralState().defenceReady(helper.getLevel().getGameTime()),
                        "and armed the window that stops the very next tick taking a fresh lease");
                    helper.assertTrue(mine.spectralCounters().auraPulses() > 0L,
                        "the bound familiar keeps pulsing its owner's frozen Haste aura throughout");
                    fixture.assertArenaUnedited(arena, "defending edits no blocks");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            // Deliberately inside the first lease-plus-window span. A permanently aggressive
            // attacker does eventually earn a second bounded intercept, which is the design; what
            // must never happen is a fresh lease every tick, and that is what this pins.
            helper.runAfterDelay(95L, () -> {
                try {
                    helper.assertValueEqual(mine.spectralCounters().defenceLeases(), 1L,
                        "ninety-five ticks beside its attacker bought one lease, not ninety-five");
                    helper.assertValueEqual(mine.spectralCounters().meleeOpportunities(), 1L,
                        "and exactly one ordinary melee opportunity came out of it, still, thirty "
                            + "five ticks later: the intercept is bounded from above AND below");

                    final TagValueOutput output = TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
                    mine.saveWithoutId(output);
                    final var saved = output.buildResult().copy();
                    // The saved NBT carries the original UUID, so the original must be discarded
                    // before the copy is created or the level silently rejects the duplicate.
                    mine.discard();
                    final SpectralFamiliarEntity reloaded = (SpectralFamiliarEntity)
                        ModEntities.ALL.get("spectral_familiar").get()
                            .create(helper.getLevel(), EntitySpawnReason.LOAD);
                    helper.assertTrue(reloaded != null,
                        "the registered type must recreate the saved familiar");
                    reloaded.load(TagValueInput.create(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
                    fixture.track(reloaded);

                    helper.assertValueEqual(reloaded.spectralState().phase(), Phase.DORMANT,
                        "no episode resumes across the seam, so no signal can replay");
                    helper.assertFalse(reloaded.spectralState().signalSpent(),
                        "and the spent flag comes back cleared with the episode that owned it");
                    helper.assertTrue(reloaded.spectralState().guideBlock().isEmpty(),
                        "no remembered guide position survives a reload");
                    helper.assertTrue(reloaded.spectralState().defenceTargetId().isEmpty(),
                        "no defence lease survives a reload");
                    helper.assertTrue(reloaded.getTarget() == null,
                        "no live target survives a load");
                    helper.assertValueEqual(reloaded.spectralCounters().meleeOpportunities(), 0L,
                        "a reloaded familiar has taken no swing merely because time passed");
                    helper.assertValueEqual(reloaded.spectralCounters().signalsEmitted(), 0L,
                        "and emitted no signal merely because it loaded");
                    helper.assertTrue(CreatureBehaviorState.isOwnedBy(reloaded, owner.getUUID()),
                        "the binding itself does survive the reload");
                    helper.assertTrue(reloaded.isPersistenceRequired(),
                        "and so does the clearable reason it persists for");
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

    // ---- fixture plumbing ----

    /** The arena's disposable hostile body. Not a familiar, not owned, and legal to defend against. */
    private static final class Aggressor extends net.minecraft.world.entity.monster.zombie.Zombie {
        private Aggressor(final ServerLevel level) {
            super(net.minecraft.world.entity.EntityTypes.ZOMBIE, level);
        }
    }

    private static final class FixtureScope implements AutoCloseable {

        /**
         * Every position this fixture is allowed to claim: the arena interior plus the floor plate
         * directly beneath it.
         *
         * <p>Thirty-six relative coordinates, all inside -1 to 2 and so well under sixteen, which
         * keeps every read inside the arena's own ticked chunk and out of a neighbour's. The floor
         * plate is in the list because leaving it out was a real blind spot: this chassis settles one
         * layer lower than it is placed - {@code Vex.tick} only disables gravity once the body has
         * ticked - so the block a hovering familiar sits directly above is at {@code y = -1}, and a
         * familiar that broke or replaced exactly that block would have been invisible to a snapshot
         * of the interior alone. Found by sabotaging the runtime into a real block edit and watching
         * the interior-only snapshot pass.</p>
         */
        private static final List<BlockPos> ARENA_FOOTPRINT = arenaFootprint();

        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private final List<Runnable> cleanupActions = new ArrayList<>();
        private boolean closed;

        private FixtureScope(final GameTestHelper helper) {
            this.helper = helper;
        }

        private static List<BlockPos> arenaFootprint() {
            final List<BlockPos> positions = new ArrayList<>(36);
            for (int x = 0; x <= 2; x++) {
                for (int y = -1; y <= 2; y++) {
                    for (int z = 0; z <= 2; z++) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
            return List.copyOf(positions);
        }

        /** The arena exactly as the fixture built it, captured once placement has finished. */
        private List<BlockState> snapshotArena() {
            final List<BlockState> states = new ArrayList<>(ARENA_FOOTPRINT.size());
            for (final BlockPos position : ARENA_FOOTPRINT) {
                states.add(helper.getBlockState(position));
            }
            return List.copyOf(states);
        }

        /**
         * The real "this family never edits a block" assertion.
         *
         * <p>It replaces an asserted zero on a world-edit counter that had no increment site
         * anywhere and therefore could not fail however the runtime behaved. This walks the arena's
         * actual block states instead: a familiar that placed, broke, replaced or waterlogged
         * anything moves one of these twenty-seven states and the fixture says which position
         * changed and into what. Block states are canonical instances, so identity is the exact
         * comparison.</p>
         */
        private void assertArenaUnedited(final List<BlockState> before, final String claim) {
            for (int index = 0; index < ARENA_FOOTPRINT.size(); index++) {
                final BlockPos position = ARENA_FOOTPRINT.get(index);
                final BlockState now = helper.getBlockState(position);
                helper.assertTrue(now == before.get(index),
                    claim + ": the block at relative " + position.toShortString() + " changed from "
                        + before.get(index) + " to " + now);
            }
        }

        private <T extends Entity> T place(final T entity, final BlockPos position) {
            final BlockPos absolute = helper.absolutePos(position);
            entity.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
            entity.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(entity);
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                // Sensing caches line of sight per server tick; repositioning invalidates that cache.
                mob.getSensing().tick();
            }
            entities.add(entity);
            return entity;
        }

        @SuppressWarnings("unchecked")
        private SpectralFamiliarEntity spawnFamiliar(final BlockPos position) {
            return place(new SpectralFamiliarEntity(
                (net.minecraft.world.entity.EntityType<? extends Vex>)
                    ModEntities.ALL.get("spectral_familiar").get(),
                helper.getLevel()), position);
        }

        private Aggressor spawnAggressor(final BlockPos position) {
            return place(new Aggressor(helper.getLevel()), position);
        }

        /**
         * A solid floor across the bottom interior layer. The spectral familiar needs no footing,
         * but the mock player and the aggressor do, and an ore laid on air is not a scene.
         */
        private void floor() {
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    placeBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
        }

        private void placeBlock(final BlockPos position, final net.minecraft.world.level.block.Block block) {
            final net.minecraft.world.level.block.state.BlockState previous =
                helper.getBlockState(position);
            helper.setBlock(position, block.defaultBlockState());
            cleanupActions.add(() -> helper.setBlock(position, previous));
        }

        private ServerPlayer connectedPlayer(final BlockPos position) {
            final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            final net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            final net.minecraft.server.network.CommonListenerCookie cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(
                    player.getGameProfile(), false);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            final BlockPos absolute = helper.absolutePos(position);
            player.teleportTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
            entities.add(player);
            return player;
        }

        private <T extends Entity> T track(final T entity) {
            entities.add(entity);
            return entity;
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
