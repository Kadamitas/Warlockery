package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Action;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Reason;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.ritual.RitualManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Nine bounded live F23/F25/F26 fixtures for the three bound animal familiars.
 *
 * <p>Every body is built through its registered {@code EntityType} factory rather than through a
 * constructor call, so the three "the registered id constructs the dedicated body" assertions are
 * claims about the registry instead of restatements of the line above them. That makes all nine
 * depend on the coordinator-deferred {@code ModEntities} routing for {@code warlockery:familiar_cat},
 * {@code warlockery:owl} and {@code warlockery:toad}: until that edit lands they fail with a cast
 * against {@code ArcaneMob}, which is the intended deferred-wiring red rather than a silent pass.
 * They also depend on the deferred {@code ModGameTests} registration to be dispatched at all.</p>
 *
 * <p>Every fixture that reads blocks wider than the arena runs in
 * {@code warlockery:animal_familiar_isolated} and carries an armed contamination control: the home
 * assertions pin the <em>exact</em> claimed position, so a bed, log or pond belonging to a
 * neighbouring arena cannot pass silently. It would move the claim and fail loudly.</p>
 *
 * <p>"No familiar edits a block" is asserted against the arena, not against a counter. The counter
 * that used to carry that claim was incremented nowhere, so its zero could not have failed;
 * {@link FixtureScope#snapshot} records every block state the familiars can reach and
 * {@link FixtureScope#assertUnchanged} compares them at the end of the run.</p>
 */
public final class AnimalFamiliarGameTests {

    private AnimalFamiliarGameTests() {
    }

    // =================================================================================
    // F1: three bodies, one controller, and no generic writer anywhere near them
    // =================================================================================

    public static void animalFamiliarsAreThreeDistinctBodies(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.floor(0, 2, 0, 2);
            final Map<BlockPos, BlockState> before = fixture.snapshot(
                new BlockPos(-1, -1, -1), new BlockPos(3, 4, 3));
            final FamiliarCatEntity cat = fixture.spawnCat(new BlockPos(0, 1, 0));
            final OwlEntity owl = fixture.spawnOwl(new BlockPos(1, 1, 0));
            final ToadEntity toad = fixture.spawnToad(new BlockPos(2, 1, 0));

            helper.assertValueEqual(cat.creatureKind(), CreatureKind.CAT, "exact cat kind");
            helper.assertValueEqual(owl.creatureKind(), CreatureKind.OWL, "exact owl kind");
            helper.assertValueEqual(toad.creatureKind(), CreatureKind.TOAD, "exact toad kind");

            // The 1.4 shared goal set is gone. Those two were the movement and target writers.
            for (final AnimalFamiliarMob body : List.of(cat, owl, toad)) {
                final List<String> goals = body.operationalGoalNames();
                helper.assertFalse(goals.contains("WaterAvoidingRandomStrollGoal"),
                    body.species() + " must not keep the generic stroll goal as a second mover");
                helper.assertFalse(goals.contains("MeleeAttackGoal"),
                    body.species() + " must not keep a second target authority");
                helper.assertTrue(goals.contains("FloatGoal"), "float stays: it is presentation");
                helper.assertValueEqual(body.operationalTargetGoalCount(), 0,
                    body.species() + " keeps no target-selector goal at all");
                helper.assertFalse(body.convertsInWater(),
                    body.species() + " is a familiar and must never drown into a Drowned");
            }

            // Chassis: this is the whole of what the three bodies add over the shared body.
            helper.assertTrue(owl.isNoGravity(), "the owl is a no-gravity flyer");
            helper.assertFalse(cat.isNoGravity(), "the cat walks");
            helper.assertFalse(toad.isNoGravity(), "the toad hops on the ground");
            helper.assertTrue(owl.getNavigation() instanceof FlyingPathNavigation,
                "the owl navigates through the air");
            helper.assertFalse(cat.getNavigation() instanceof FlyingPathNavigation,
                "the cat navigates on the ground");
            helper.assertValueEqual(owl.getAttributeValue(Attributes.FLYING_SPEED),
                OwlEntity.FLYING_SPEED,
                "a flyer without a declared flying speed cannot be constructed at all");

            helper.runAfterDelay(60L, () -> {
                try {
                    for (final AnimalFamiliarMob body : List.of(cat, owl, toad)) {
                        helper.assertTrue(body.familiarCounters().decisions() > 0L,
                            body.species() + " must actually reach its own tick");
                        helper.assertTrue(body.familiarCounters().genericLayersDeclined() > 0L,
                            body.species() + " must reach and decline the generic layers");
                        helper.assertValueEqual(body.familiarCounters().auraPulses(), 0L,
                            "an unbound familiar grants no aura to anybody");
                        helper.assertTrue(AnimalFamiliarRules.permits(
                                body.species(), body.lastFamiliarDecision().action()),
                            body.species() + " emitted an action outside its own vocabulary");
                    }
                    // The real claim the deleted counter was pretending to make.
                    fixture.assertUnchanged(before);
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

    public static void familiarBindingHonoursTheVanillaLatchAndRefusesTheContractOne(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final FamiliarCatEntity latched = fixture.spawnCat(new BlockPos(0, 1, 0));
            final ToadEntity bound = fixture.spawnToad(new BlockPos(2, 1, 2));

            // Half one: every ordinary latch write is honoured exactly as it always was. This is
            // the half the F10 review rejected a remedy for, because discarding it would make a
            // name-tagged familiar despawn.
            helper.assertFalse(latched.vanillaPersistenceLatched(),
                "a directly constructed familiar starts genuinely unlatched");
            latched.setPersistenceRequired();
            helper.assertTrue(latched.vanillaPersistenceLatched(),
                "a name tag, a dispenser, a hopper or a command latches a familiar normally");

            // Half two: the one unclearable write made inside the shared contract binding is
            // refused at its source. This goes through the real player interaction, not a seam.
            helper.assertFalse(bound.vanillaPersistenceLatched(), "still unlatched before binding");
            helper.assertFalse(bound.contractLatchSuppressed(), "the window is shut outside interact");
            final ServerPlayer player = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            final ItemStack binder = new ItemStack(Items.STRING, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, binder);
            player.interactOn(bound, InteractionHand.MAIN_HAND, Vec3.ZERO);

            helper.assertTrue(CreatureBehaviorState.isOwnedBy(bound, player.getUUID()),
                "the frozen companion binder acquisition still binds the familiar to its player");
            helper.assertValueEqual(binder.getCount(), 1,
                "binding still consumes exactly one binder, unchanged from 1.4");
            helper.assertFalse(bound.vanillaPersistenceLatched(),
                "the unclearable contract latch write is refused at its source");
            helper.assertFalse(bound.contractLatchSuppressed(),
                "the suppression window is closed again in the finally arm");
            helper.assertTrue(bound.isPersistenceRequired(),
                "a bound familiar still persists, through its own explicit and clearable reason");

            CreatureBehaviorState.unbind(bound);
            helper.assertFalse(bound.isPersistenceRequired(),
                "unbinding clears this family's own reason, exactly as before");
            helper.assertTrue(latched.isPersistenceRequired(),
                "the name-tag style latch is untouched by any of this");

            helper.runAfterDelay(30L, () -> {
                try {
                    helper.assertTrue(bound.familiarCounters().decisions() > 0L,
                        "the toad keeps ticking through all of this");
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
    // F3: the cat claims a household and then walks its own ring
    // =================================================================================

    public static void familiarCatClaimsAHouseholdAndPatrolsIt(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            // A tagged household block beside the claim. The cat's home predicate is "beside a bed
            // or a familiar home block", which is not the owl's and not the toad's.
            fixture.floor(0, 2, 0, 2);
            fixture.placeBlock(new BlockPos(2, 1, 1), Blocks.HAY_BLOCK);
            helper.setTime(6_000L);
            final Map<BlockPos, BlockState> before = fixture.snapshot(
                new BlockPos(-1, -1, -1), new BlockPos(3, 4, 3));
            final FamiliarCatEntity cat = fixture.spawnCat(new BlockPos(1, 1, 1));
            cat.makeFamiliarSearchesDue();

            helper.runAfterDelay(20L, () -> {
                try {
                    final AnimalFamiliarState state = cat.familiarState();
                    helper.assertTrue(state.home().isPresent(),
                        "the bounded search claims a household");
                    // Armed contamination control: the exact position, not merely "a" position. A
                    // neighbouring arena's bed would move this and fail loudly.
                    helper.assertValueEqual(state.home().orElseThrow(),
                        helper.absolutePos(new BlockPos(1, 1, 1)),
                        "the claim is the nearest qualifying position, inside this arena");
                    helper.assertValueEqual(cat.familiarCounters().homeClaims(), 1L,
                        "exactly one claim, not one per tick");
                    final var profile = AnimalFamiliarRules.profile(AnimalFamiliarSpecies.CAT);
                    helper.assertValueEqual(cat.familiarCounters().homeSearches(), 1L,
                        "exactly one search ran, so the per-search bounds below mean one search");
                    helper.assertTrue(cat.familiarCounters().homeCandidatesInspected()
                            <= profile.homePositionsPerScan(),
                        "a scan inspects at most its own window of "
                            + profile.homePositionsPerScan() + " positions");
                    // Both bounds. The upper one alone is satisfied by zero, which is how a search
                    // that never left its own block would have passed this fixture.
                    helper.assertTrue(cat.familiarCounters().homeBlockReads()
                            >= profile.homePositionsPerScan(),
                        "every position in the window is charged, so the reads cannot be fewer than "
                            + profile.homePositionsPerScan());
                    helper.assertTrue(cat.familiarCounters().homeBlockReads()
                            <= profile.homeReadCap(),
                        "and the budget still binds at " + profile.homeReadCap());
                    cat.makeFamiliarSearchesDue();
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(70L, () -> {
                try {
                    helper.assertValueEqual(cat.lastFamiliarDecision().action(),
                        Action.PATROL_TERRITORY,
                        "an awake cat with a household walks its own ring, which no other species has");
                    helper.assertTrue(cat.familiarCounters().navigationRequests() > 0L,
                        "the runtime is the movement writer and it did write");
                    // Night: the same cat stops patrolling and settles. The owl and the toad do the
                    // opposite with the same clock.
                    helper.setTime(18_000L);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(110L, () -> {
                try {
                    helper.assertValueEqual(cat.lastFamiliarDecision().action(), Action.CURL_AT_HOME,
                        "a cat is a daylight animal and rests at its household through the night");
                    fixture.assertUnchanged(before);
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
    // F4: the owl hangs from a perch, the toad sits by water, and neither takes the other's
    // =================================================================================

    public static void owlPerchAndToadShelterStaySpeciesSpecific(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            // The owl's support hangs above its perch; the toad's shelter is overhead cover beside
            // standing water. Both blocks are in both tags on purpose, so the only thing separating
            // the two claims is each species' own footing and predicate.
            fixture.floor(0, 2, 0, 2);
            fixture.placeBlock(new BlockPos(1, 3, 1), Blocks.OAK_LOG);
            fixture.placeBlock(new BlockPos(2, 2, 1), Blocks.OAK_LEAVES);
            fixture.placeBlock(new BlockPos(0, 1, 1), Blocks.WATER);
            helper.setTime(6_000L);

            final OwlEntity owl = fixture.spawnOwl(new BlockPos(1, 2, 1));
            final ToadEntity toad = fixture.spawnToad(new BlockPos(2, 1, 1));
            owl.makeFamiliarSearchesDue();
            toad.makeFamiliarSearchesDue();

            helper.runAfterDelay(25L, () -> {
                try {
                    final AnimalFamiliarState owlState = owl.familiarState();
                    final AnimalFamiliarState toadState = toad.familiarState();
                    helper.assertTrue(owlState.home().isPresent(),
                        "the owl finds a supported perch to hang from");
                    helper.assertValueEqual(owlState.home().orElseThrow(),
                        helper.absolutePos(new BlockPos(1, 2, 1)),
                        "the perch is the exact air position under the tagged support");
                    helper.assertTrue(toadState.home().isPresent(),
                        "the toad finds a sheltered dry footprint beside water");
                    helper.assertValueEqual(toadState.home().orElseThrow(),
                        helper.absolutePos(new BlockPos(2, 1, 1)),
                        "the shelter is the exact covered position within reach of the pond");
                    helper.assertFalse(
                        owlState.home().orElseThrow().equals(toadState.home().orElseThrow()),
                        "a perch is not a shelter: the two species claim different places");
                    helper.assertValueEqual(owl.familiarCounters().homeClaims(), 1L, "one owl claim");
                    helper.assertValueEqual(toad.familiarCounters().homeClaims(), 1L, "one toad claim");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(80L, () -> {
                try {
                    helper.assertTrue(AnimalFamiliarRules.permits(
                            AnimalFamiliarSpecies.OWL, owl.lastFamiliarDecision().action()),
                        "the perched owl remains inside its own action vocabulary");
                    helper.assertTrue(AnimalFamiliarRules.permits(
                            AnimalFamiliarSpecies.TOAD, toad.lastFamiliarDecision().action()),
                        "the toad stays inside its own vocabulary");
                    helper.assertFalse(AnimalFamiliarRules.permits(
                            AnimalFamiliarSpecies.TOAD, Action.GLIDE_SURVEY),
                        "no toad may ever be scheduled an owl action");
                    helper.assertFalse(AnimalFamiliarRules.permits(
                            AnimalFamiliarSpecies.OWL, Action.SHELTER_REST),
                        "no owl may ever be scheduled a toad action");

                    // Support loss releases the claim without a world edit and without a cooldown.
                    helper.setBlock(new BlockPos(1, 3, 1), Blocks.AIR.defaultBlockState());
                    owl.makeFamiliarSearchesDue();
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(160L, () -> {
                try {
                    helper.assertTrue(owl.familiarCounters().homeReleases() > 0L
                            || owl.familiarState().home().isEmpty(),
                        "losing the support releases the perch claim");
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
    // F5: one defensive lease, two owners stay isolated, and a reload replays nothing
    // =================================================================================

    public static void familiarOwnerDefenceIsOneLeaseAndReloadNeverReplays(
        final GameTestHelper helper
    ) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.floor(0, 2, 0, 2);
            final Map<BlockPos, BlockState> before = fixture.snapshot(
                new BlockPos(-1, -1, -1), new BlockPos(3, 4, 3));
            final FamiliarCatEntity mine = fixture.spawnCat(new BlockPos(0, 1, 0));
            final ToadEntity theirs = fixture.spawnToad(new BlockPos(2, 1, 0));
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(2, 1, 2));
            CreatureBehaviorState.bind(mine, owner.getUUID());
            CreatureBehaviorState.bind(theirs, java.util.UUID.randomUUID());

            // Inside the cat's melee reach from the start, so the one intercept the lease buys is
            // taken on the tick it is opened rather than depending on a navigation race.
            final Aggressor aggressor = fixture.spawnAggressor(new BlockPos(1, 1, 0));
            aggressor.setNoAi(true);
            owner.setLastHurtByMob(aggressor);

            helper.runAfterDelay(5L, () -> {
                try {
                    helper.assertValueEqual(mine.familiarCounters().defenceLeases(), 1L,
                        "a direct legal attack on the owner yields exactly one defensive lease");
                    // The lease is spent on the tick it opens, because the attacker is already
                    // inside melee reach, so the rung has moved on by now and asserting it here
                    // would be a race. What it spent the lease on is asserted instead, and the
                    // ladder's ordering is pinned exactly by AnimalFamiliarRulesTest.
                    helper.assertValueEqual(mine.familiarCounters().meleeOpportunities(), 1L,
                        "and spends it on exactly one ordinary melee opportunity");
                    helper.assertTrue(aggressor.getLastHurtByMob() == mine,
                        "the swing landed: the attacker records this cat as what hurt it");
                    helper.assertTrue(mine.familiarState().defenceCooldownUntil() > 0L,
                        "and spending it armed the one-intercept window");

                    // The contamination the brewing bug is about: another player's familiar
                    // standing right here is not co-opted by this player's fight.
                    helper.assertValueEqual(theirs.familiarCounters().defenceLeases(), 0L,
                        "another player's familiar never takes a lease from this owner's attacker");
                    helper.assertTrue(theirs.getTarget() == null,
                        "and never acquires a target from it either");
                    helper.assertValueEqual(theirs.familiarCounters().auraPulses(), 0L,
                        "nor does it pulse an aura for an owner that is not loaded for it");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(60L, () -> {
                try {
                    helper.assertValueEqual(mine.familiarCounters().defenceLeases(), 1L,
                        "one attack event yields one lease and never a stream of them");
                    // Exactly one, not "at most one". At most one is satisfied by none, which is
                    // how a defence that never connected would have passed this fixture.
                    helper.assertValueEqual(mine.familiarCounters().meleeOpportunities(), 1L,
                        "a lease is exactly one ordinary melee opportunity, never a chain");
                    helper.assertTrue(mine.familiarCounters().auraPulses() > 0L,
                        "the bound cat keeps pulsing its own owner's frozen Luck aura throughout");
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
                    helper.assertValueEqual(mine.familiarCounters().defenceLeases(), 1L,
                        "ninety-five ticks beside its attacker bought exactly one lease, not ninety");
                    helper.assertValueEqual(mine.familiarCounters().meleeOpportunities(), 1L,
                        "and exactly one ordinary melee opportunity came out of it");

                    final TagValueOutput output = TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
                    mine.saveWithoutId(output);
                    final var saved = output.buildResult().copy();
                    // The saved NBT carries the original UUID, so the original must be discarded
                    // before the copy is created or the level silently rejects the duplicate.
                    mine.discard();
                    final FamiliarCatEntity reloaded = (FamiliarCatEntity)
                        ModEntities.ALL.get("familiar_cat").get()
                            .create(helper.getLevel(), EntitySpawnReason.LOAD);
                    helper.assertTrue(reloaded != null,
                        "the registered type must recreate the saved familiar");
                    reloaded.load(TagValueInput.create(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
                    fixture.track(reloaded);

                    helper.assertValueEqual(reloaded.familiarState().phase(),
                        AnimalFamiliarState.Phase.NONE,
                        "no signature action resumes across the seam, so no attack can replay");
                    helper.assertTrue(reloaded.familiarState().defenceTargetId().isEmpty(),
                        "no defence lease survives a reload");
                    helper.assertTrue(reloaded.getTarget() == null, "no live target survives a load");
                    helper.assertValueEqual(reloaded.familiarCounters().meleeOpportunities(), 0L,
                        "a reloaded familiar has taken no swing merely because time passed");
                    helper.assertTrue(CreatureBehaviorState.isOwnedBy(reloaded, owner.getUUID()),
                        "the binding itself does survive the reload");
                    helper.assertValueEqual(reloaded.species(), AnimalFamiliarSpecies.CAT,
                        "and it comes back as the same species it was saved as");
                    fixture.assertUnchanged(before);
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
    // F6: the home search reaches past its own block, which is all the old fixtures proved
    // =================================================================================

    /**
     * The one fixture the delivered package did not have: a home claimed at a real distance.
     *
     * <p>Every other home assertion in this file pins a position the familiar is standing on or
     * beside, so all of them would have passed against a search that evaluated offset zero and
     * stopped. The delivered arithmetic did almost exactly that -- it requested a
     * {@code ScanEnvelope} window whose length was the READ cap while a position costs seven reads,
     * so the cat's scan died at squared offset two. The household here is at squared offset four,
     * outside that reach and inside the fixed near anchor, so it is claimed on the first scan after
     * the fix and on no scan before it.</p>
     */
    public static void familiarHomeClaimReachesPastTheInnermostRing(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            // The floor runs one block past the arena wall so the far claim has real footing.
            fixture.floor(0, 3, 0, 2);
            // The only tagged household anywhere near this cat, and the only position beside it
            // that is both clear and supported is (2,1,1) -- squared offset four from the cat's own
            // block. Nothing within squared offset two qualifies, so a search that never leaves the
            // innermost ring claims nothing at all.
            fixture.placeBlock(new BlockPos(3, 1, 1), Blocks.HAY_BLOCK);
            helper.setTime(6_000L);
            final FamiliarCatEntity cat = fixture.spawnCat(new BlockPos(0, 1, 1));
            cat.makeFamiliarSearchesDue();

            helper.runAfterDelay(20L, () -> {
                try {
                    final var profile = AnimalFamiliarRules.profile(AnimalFamiliarSpecies.CAT);
                    helper.assertValueEqual(cat.familiarCounters().homeSearches(), 1L,
                        "exactly one scan ran, so every number below describes one scan");
                    helper.assertTrue(cat.familiarState().home().isPresent(),
                        "a household four squared blocks away is inside the advertised envelope "
                            + "and must be found");
                    helper.assertValueEqual(cat.familiarState().home().orElseThrow(),
                        helper.absolutePos(new BlockPos(2, 1, 1)),
                        "the claim is the exact clear, supported position beside the hay, and it is "
                            + "not the cat's own block");
                    helper.assertTrue(cat.familiarState().home().orElseThrow()
                            .distSqr(helper.absolutePos(new BlockPos(0, 1, 1))) >= 4.0,
                        "and it is genuinely away from the body rather than under it");
                    // The old scan could retain at most twenty-four candidates and reached only
                    // about thirteen positions before its budget died. Both of these are out of
                    // reach of that code by construction.
                    helper.assertTrue(cat.familiarCounters().homeCandidatesInspected() > 24L,
                        "one scan inspects far more than the retired candidate cap of twenty-four");
                    helper.assertTrue(cat.familiarCounters().homeCandidatesInspected()
                            <= profile.homePositionsPerScan(),
                        "and never more than its own window of " + profile.homePositionsPerScan());
                    helper.assertTrue(cat.familiarCounters().homeBlockReads()
                            >= profile.homePositionsPerScan(),
                        "every window position is charged before it can be judged");
                    helper.assertTrue(cat.familiarCounters().homeBlockReads()
                            <= profile.homeReadCap(),
                        "and the whole scan still fits inside " + profile.homeReadCap() + " reads");
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
    // F7: an unbound familiar stays, and nothing else about persistence moves
    // =================================================================================

    /**
     * The owner ruling, asserted against the exact gate {@code Mob.checkDespawn} runs at 26.2.
     *
     * <p>Both {@code discard()} calls in the distance branch are guarded by
     * {@code removeWhenFarAway}, and the branch itself is guarded by {@code isPersistenceRequired}
     * and {@code requiresCustomPersistence}. This walks all four, with a plain zombie in the same
     * arena as the control that says the assertions can fail.</p>
     */
    public static void unboundFamiliarsPersistAndNoLatchIsDisturbed(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.floor(0, 2, 0, 2);
            final FamiliarCatEntity cat = fixture.spawnCat(new BlockPos(0, 1, 0));
            final OwlEntity owl = fixture.spawnOwl(new BlockPos(1, 1, 0));
            final ToadEntity toad = fixture.spawnToad(new BlockPos(2, 1, 0));
            final Aggressor control = fixture.spawnAggressor(new BlockPos(1, 1, 2));
            control.setNoAi(true);

            // The control. Without it "false" here would be unfalsifiable prose.
            helper.assertTrue(control.removeWhenFarAway(1_000_000.0),
                "an ordinary zombie in this same arena does distance-despawn");

            for (final AnimalFamiliarMob body : List.of(cat, owl, toad)) {
                helper.assertFalse(body.removeWhenFarAway(1_000_000.0),
                    "an unbound " + body.species() + " is never removed for being far away");
                helper.assertFalse(body.isPersistenceRequired(),
                    "and it is not latched persistent either: the ruling is not a latch");
                helper.assertFalse(body.requiresCustomPersistence(),
                    "nor does it claim custom persistence, which would hide it from the "
                        + "natural spawn cap and make the accumulation unbounded");
                helper.assertTrue(body.getType().isAllowedInPeaceful(),
                    body.species() + " is allowed in peaceful, so the peaceful sweep is untouched");
            }

            // The latch the F10 review protected is still a latch, and it does not become the
            // mechanism for any of the above.
            cat.setPersistenceRequired();
            helper.assertTrue(cat.vanillaPersistenceLatched(),
                "a name tag still latches a familiar exactly as it always did");
            helper.assertFalse(cat.removeWhenFarAway(1_000_000.0),
                "and a latched familiar is still not distance-despawned");

            // The bound half of the contract is unchanged: binding is still what makes
            // isPersistenceRequired true, and unbinding still clears it.
            CreatureBehaviorState.bind(toad, java.util.UUID.randomUUID());
            helper.assertTrue(toad.isPersistenceRequired(),
                "binding is still the reason a bound familiar persists");
            CreatureBehaviorState.unbind(toad);
            helper.assertFalse(toad.isPersistenceRequired(),
                "and unbinding still clears that reason rather than sticking forever");
            helper.assertFalse(toad.removeWhenFarAway(1_000_000.0),
                "while the unbound toad still stays in the world, which is the ruling");

            helper.runAfterDelay(40L, () -> {
                try {
                    for (final AnimalFamiliarMob body : List.of(cat, owl, toad)) {
                        helper.assertTrue(body.isAlive() && !body.isRemoved(),
                            body.species() + " is still here after ticking unbound");
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

    // =================================================================================
    // F8: a summon that does something
    // =================================================================================

    /**
     * The two halves of the brew regression, both live.
     *
     * <p>{@code BrewRuntime.summonOwls} spawns three owls and calls {@code setTarget} on each.
     * Nothing in this family read {@code getTarget()}, and the constructor had already removed the
     * {@code MeleeAttackGoal} that used to act on it, so those owls held a target forever and did
     * nothing with it. {@code BrewRuntime.summonPoisonToads} spawns four unbound toads whose ladder
     * resolves to {@code IDLE}, and {@code IDLE} used to mean {@code navigation.stop()}, so the
     * brew's visible effect was four statues.</p>
     */
    public static void aSummonedFamiliarActsOnWhatItIsGiven(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.floor(0, 2, 0, 2);
            fixture.clearTaggedHerbLandmarks(new BlockPos(2, 1, 2), 9, 6);
            helper.setTime(6_000L);
            // Armed precondition. A toad wakes at night or in rain, and this half of the fixture is
            // about what a toad does while it is NOT awake, so a wet arena must fail loudly rather
            // than quietly test the wrong rung.

            final OwlEntity owl = fixture.spawnOwl(new BlockPos(1, 2, 1));
            CreatureBehaviorState.bind(owl, java.util.UUID.randomUUID());
            final Aggressor quarry = fixture.spawnAggressor(new BlockPos(1, 1, 1));
            quarry.setNoAi(true);
            // Exactly what the brew does with the owls it summons.
            owl.setTarget(quarry);

            final ToadEntity toad = fixture.spawnToad(new BlockPos(2, 1, 2));
            toad.makeFamiliarSearchesDue();

            helper.runAfterDelay(10L, () -> {
                try {
                    // Nothing here hurt the owl and its owner is not loaded, so the only threat
                    // source that can produce a lease is the target the brew handed it. Under the
                    // delivered code, which read getTarget() nowhere, every one of these is zero.
                    helper.assertValueEqual(owl.familiarCounters().defenceLeases(), 1L,
                        "an assigned target is a threat this runtime actually reads, and it becomes "
                            + "exactly one bounded intercept rather than a standing target");
                    helper.assertValueEqual(owl.familiarCounters().meleeOpportunities(), 1L,
                        "which is spent on exactly one ordinary melee opportunity");
                    helper.assertTrue(quarry.getLastHurtByMob() == owl,
                        "the strike landed: the quarry records this owl as what hurt it");
                    helper.assertTrue(owl.familiarState().defenceCooldownUntil() > 0L,
                        "and spending it armed the window, so this can never become a chain");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });

            helper.runAfterDelay(80L, () -> {
                try {
                    final long previousTime = helper.getLevel().getDefaultClockTime();
                    final net.minecraft.world.level.saveddata.WeatherData weather =
                        helper.getLevel().getWeatherData();
                    final boolean raining = weather.isRaining();
                    final boolean thundering = weather.isThundering();
                    helper.setTime(6_000L);
                    weather.setRaining(false);
                    weather.setThundering(false);
                    helper.getLevel().setRainLevel(0.0F);
                    helper.getLevel().setThunderLevel(0.0F);
                    toad.customServerAiStep(helper.getLevel());
                    helper.assertValueEqual(toad.lastFamiliarDecision().action(), Action.IDLE,
                        "an unbound toad on a dry day with no shelter has nothing better to do");
                    helper.assertValueEqual(toad.lastFamiliarDecision().reason(),
                        Reason.NOTHING_TO_DO,
                        "and it is there because nothing qualified, not because it is broken");
                    // The whole point. Under the delivered code this rung called
                    // navigation.stop() and this counter stayed at zero forever.
                    helper.assertTrue(toad.familiarCounters().navigationRequests() > 0L,
                        "an idle familiar drifts, because these three no longer despawn and a "
                            + "permanent statue is not a familiar");
                    helper.assertTrue(toad.familiarCounters().navigationRequests() <= 5L,
                        "and it drifts on the shared navigation interval, not every tick");
                    helper.assertValueEqual(owl.familiarCounters().meleeOpportunities(), 1L,
                        "the owl's one intercept stays one across the whole run");
                    helper.setTime(previousTime);
                    weather.setRaining(raining);
                    weather.setThundering(thundering);
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

    public static void familiarBindingConvertsVanillaCatAndFrogTransactionally(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.floor(0, 4, 0, 2);
            final ServerPlayer caster = fixture.connectedPlayer(new BlockPos(0, 1, 0));
            final Cat cat = fixture.track(EntityTypes.CAT.create(helper.getLevel(), EntitySpawnReason.EVENT));
            final Frog frog = fixture.track(EntityTypes.FROG.create(helper.getLevel(), EntitySpawnReason.EVENT));
            helper.assertTrue(cat != null && frog != null, "vanilla conversion sources must construct");
            cat.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 0))));
            frog.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 0))));
            cat.setCustomName(net.minecraft.network.chat.Component.empty().append("Blackthorn"));
            cat.setCustomNameVisible(true);
            cat.setBaby(true);
            cat.tame(caster);
            final java.util.UUID catSourceId = cat.getUUID();
            final java.util.UUID frogSourceId = frog.getUUID();
            helper.getLevel().addFreshEntity(cat);
            helper.getLevel().addFreshEntity(frog);

            helper.assertTrue(AnimalFamiliarBindingRuntime.bind(helper.getLevel(), cat, caster, 2_400),
                "an eligible caster-owned vanilla cat converts");
            helper.assertTrue(AnimalFamiliarBindingRuntime.bind(helper.getLevel(), frog, caster, 2_400),
                "an eligible vanilla frog converts");

            final FamiliarCatEntity replacementCat = helper.getLevel()
                .getEntitiesOfClass(FamiliarCatEntity.class, cat.getBoundingBox().inflate(2.0)).stream()
                .findFirst().orElseThrow();
            final ToadEntity replacementToad = helper.getLevel()
                .getEntitiesOfClass(ToadEntity.class, frog.getBoundingBox().inflate(2.0)).stream()
                .findFirst().orElseThrow();
            fixture.track(replacementCat);
            fixture.track(replacementToad);
            helper.assertFalse(cat.isAlive(), "the cat source is discarded only after replacement addition");
            helper.assertFalse(frog.isAlive(), "the frog source is discarded only after replacement addition");
            helper.assertFalse(replacementCat.getUUID().equals(catSourceId), "cat replacement receives a new UUID");
            helper.assertFalse(replacementToad.getUUID().equals(frogSourceId), "toad replacement receives a new UUID");
            helper.assertValueEqual(replacementCat.getCustomName().getString(), "Blackthorn", "custom name transfers");
            helper.assertTrue(replacementCat.isCustomNameVisible(), "name visibility transfers");
            helper.assertTrue(replacementCat.isBaby(), "baby state transfers");
            helper.assertValueEqual(CreatureBehaviorState.owner(replacementCat).orElseThrow(), caster.getUUID(),
                "converted cat belongs to the caster");
            helper.assertValueEqual(CreatureBehaviorState.owner(replacementToad).orElseThrow(), caster.getUUID(),
                "converted frog becomes the caster's toad");
            // The rite scans its full five-block radius. Retire the successful subjects before
            // each refusal case so that the assertion addresses that exact source, not a nearer
            // already-bound familiar from the preceding transaction.
            replacementCat.discard();
            replacementToad.discard();

            final ServerPlayer foreignOwner = fixture.connectedPlayer(new BlockPos(0, 1, 2));
            final Cat foreignCat = fixture.track(EntityTypes.CAT.create(helper.getLevel(), EntitySpawnReason.EVENT));
            foreignCat.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))));
            foreignCat.tame(foreignOwner);
            helper.getLevel().addFreshEntity(foreignCat);
            helper.assertFalse(AnimalFamiliarBindingRuntime.bind(helper.getLevel(), foreignCat, caster, 2_400),
                "another player's tame cat is rejected");
            helper.assertTrue(foreignCat.isAlive(), "rejected foreign cat remains alive");
            helper.assertValueEqual(foreignCat.getOwnerReference().getUUID(), foreignOwner.getUUID(),
                "rejection preserves the foreign owner");

            final Frog passenger = fixture.track(EntityTypes.FROG.create(helper.getLevel(), EntitySpawnReason.EVENT));
            passenger.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 2))));
            helper.getLevel().addFreshEntity(passenger);
            passenger.startRiding(foreignCat, true, true);
            helper.assertFalse(AnimalFamiliarBindingRuntime.bind(helper.getLevel(), passenger, caster, 2_400),
                "a passenger transfer is rejected");
            helper.assertTrue(passenger.isAlive() && passenger.isPassenger(),
                "rejected passenger source remains alive and attached");

            final FamiliarCatEntity foreignFamiliar = fixture.spawnCat(new BlockPos(0, 1, 2));
            helper.assertTrue(CreatureBehaviorState.bind(foreignFamiliar, foreignOwner.getUUID()),
                "fixture familiar starts bound to the other player");
            helper.assertFalse(AnimalFamiliarBindingRuntime.bind(
                helper.getLevel(), foreignFamiliar, caster, 2_400),
                "an existing foreign familiar cannot be rebound");
            helper.assertValueEqual(CreatureBehaviorState.owner(foreignFamiliar).orElseThrow(),
                foreignOwner.getUUID(), "failed rebind preserves the existing familiar owner");

            final Frog failedAddition = fixture.track(
                EntityTypes.FROG.create(helper.getLevel(), EntitySpawnReason.EVENT));
            helper.assertTrue(failedAddition != null, "failed-addition source must construct");
            failedAddition.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 1))));
            failedAddition.setCustomName(net.minecraft.network.chat.Component.empty().append("Still here"));
            helper.getLevel().addFreshEntity(failedAddition);
            final java.util.UUID failedSourceId = failedAddition.getUUID();
            helper.assertFalse(AnimalFamiliarBindingRuntime.bind(
                helper.getLevel(), failedAddition, caster, 2_400, _ -> false
            ), "a rejected replacement addition fails the conversion");
            helper.assertTrue(failedAddition.isAlive(), "failed replacement addition leaves its source alive");
            helper.assertValueEqual(failedAddition.getUUID(), failedSourceId,
                "failed replacement addition does not mutate source identity");
            helper.assertValueEqual(failedAddition.getCustomName().getString(), "Still here",
                "failed replacement addition leaves source data untouched");
            foreignCat.discard();
            passenger.discard();
            foreignFamiliar.discard();
            failedAddition.discard();
            beginRealBindFamiliarChargeProof(helper, fixture, caster);
        } finally {
            // The asynchronous charge proof owns fixture cleanup on both success and failure.
        }
    }

    private static void beginRealBindFamiliarChargeProof(
        final GameTestHelper helper, final FixtureScope fixture, final ServerPlayer caster
    ) {
        final BlockPos relativeCenter = new BlockPos(2, 2, 2);
        final BlockPos center = helper.absolutePos(relativeCenter);
        helper.setBlock(relativeCenter.below(), Blocks.STONE);
        helper.setBlock(relativeCenter, ModBlocks.ALL.get("circle").get());
        final var definition = RitualManager.INSTANCE.byId(
            Identifier.fromNamespaceAndPath("warlockery", "bind_familiar")
        ).orElseThrow().definition();
        ChalkCircleLayout.rings(definition.glyphs()).forEach(ring -> ring.size().offsets().forEach(offset -> {
            final BlockPos relative = relativeCenter.offset(offset);
            helper.setBlock(relative.below(), Blocks.STONE);
            helper.setBlock(relative, ModBlocks.ALL.get(ring.glyph()).get());
        }));

        final BlockPos relativeAltar = new BlockPos(10, 2, 2);
        BlockPos.betweenClosedStream(relativeAltar, relativeAltar.offset(2, 0, 1))
            .forEach(position -> helper.setBlock(position, ModBlocks.ALTAR.get()));
        helper.runAfterDelay(165L, () -> {
            try {
                final AltarBlockEntity altar = helper.getLevel().getBlockEntity(
                    helper.absolutePos(relativeAltar)) instanceof AltarBlockEntity found ? found : null;
                helper.assertTrue(altar != null && altar.isMultiblockValid(),
                    "the six-block ritual altar becomes valid");
                final int ambientPower = altar.getPower();
                helper.assertValueEqual(altar.receivePower(1_500), 1_500,
                    "the altar accepts exactly one bind-familiar charge");
                final int fundedPower = ambientPower + 1_500;

                final Cat foreign = fixture.track(
                    EntityTypes.CAT.create(helper.getLevel(), EntitySpawnReason.EVENT));
                foreign.setPos(Vec3.atBottomCenterOf(center.above()));
                final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(0, 2, 2));
                foreign.tame(owner);
                helper.getLevel().addFreshEntity(foreign);
                final ItemEntity needle = fixture.track(new ItemEntity(helper.getLevel(),
                    center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                    new ItemStack(ModItems.ALL.get("ingredient_bone_needle").get())));
                final ItemEntity will = fixture.track(new ItemEntity(helper.getLevel(),
                    center.getX() + 0.8, center.getY() + 1.0, center.getZ() + 0.5,
                    new ItemStack(ModItems.ALL.get("ingredient_focused_will").get())));
                helper.getLevel().addFreshEntity(needle);
                helper.getLevel().addFreshEntity(will);
                final Identifier rite = Identifier.fromNamespaceAndPath("warlockery", "bind_familiar");
                helper.assertFalse(RitualManager.INSTANCE.activate(
                    helper.getLevel(), center, caster, rite).isEmpty(),
                    "a foreign-owned tame cat refuses before activation");
                helper.assertTrue(needle.isAlive() && will.isAlive(),
                    "a refused activation consumes neither offering");
                helper.assertValueEqual(altar.getPower(), fundedPower, "refusal drains no altar power");
                helper.assertValueEqual(altar.getEscrowedPower(), 0, "refusal creates no escrow");
                foreign.discard();

                final Frog source = fixture.track(
                    EntityTypes.FROG.create(helper.getLevel(), EntitySpawnReason.EVENT));
                source.setPos(Vec3.atBottomCenterOf(center.above()));
                source.setNoAi(true);
                source.setInvulnerable(true);
                helper.getLevel().addFreshEntity(source);
                final var sourceBox = source.getBoundingBox();
                helper.assertTrue(RitualManager.INSTANCE.activate(
                    helper.getLevel(), center, caster, rite).isEmpty(),
                    "the genuine bind-familiar activation starts");
                helper.assertFalse(needle.isAlive() || will.isAlive(),
                    "activation consumes exactly one of each real offering");
                helper.assertValueEqual(altar.getPower(), fundedPower,
                    "activation promises power without draining it early");
                helper.assertValueEqual(altar.getEscrowedPower(), 1_500,
                    "exactly one ritual power charge is escrowed");

                helper.runAfterDelay(101L, () -> {
                    try {
                        helper.assertFalse(source.isAlive(),
                            "normal session completion discards the converted source");
                        helper.assertValueEqual(altar.getEscrowedPower(), 0,
                            "completion settles the escrow exactly once");
                        helper.assertTrue(altar.getPower() >= fundedPower - 1_500
                                && altar.getPower() < fundedPower - 1_400,
                            "completion settles the exact 1500 escrow while the live altar may "
                                + "continue gathering bounded ambient power");
                        final List<AnimalFamiliarMob> replacements = helper.getLevel().getEntitiesOfClass(
                            AnimalFamiliarMob.class, sourceBox.inflate(8.0)).stream()
                            .filter(entity -> CreatureBehaviorState.isOwnedBy(entity, caster.getUUID()))
                            .toList();
                        helper.assertFalse(replacements.isEmpty(),
                            "completion leaves the newly bound familiar body near its source");
                        final AnimalFamiliarMob replacement = replacements.getFirst();
                        fixture.track(replacement);
                        helper.assertValueEqual(replacement.species(), AnimalFamiliarSpecies.TOAD,
                            "the genuine frog ritual creates the existing red toad body");
                        helper.assertValueEqual(CreatureBehaviorState.owner(replacement).orElseThrow(),
                            caster.getUUID(), "the normal ritual path creates the caster's toad");
                        helper.succeed();
                    } finally {
                        fixture.close();
                    }
                });
            } catch (final RuntimeException | Error failure) {
                fixture.close();
                throw failure;
            }
        });
    }

    public static void owlNaturalSpawnContractIsForestOnlyAndSparse(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.placeBlock(new BlockPos(1, 3, 1), Blocks.OAK_LOG);
            helper.setTime(6_000L);
            final OwlEntity owl = fixture.spawnOwl(new BlockPos(1, 2, 1));
            owl.makeFamiliarSearchesDue();
            helper.runAfterDelay(100L, () -> {
                try {
                    helper.assertTrue(owl.familiarState().home().isPresent(),
                        "an unbound owl selects a supported tagged tree perch");
                    final long previousTime = helper.getLevel().getDefaultClockTime();
                    helper.setTime(6_000L);
                    owl.customServerAiStep(helper.getLevel());
                    helper.assertValueEqual(owl.lastFamiliarDecision().action(), Action.ROOST_WATCH,
                        "a daylight unbound owl visibly roosts at its selected tree support");
                    helper.setTime(previousTime);
                    helper.assertTrue(CreatureBehaviorState.owner(owl).isEmpty(),
                        "natural owls remain unbound and gain no owner tether or aura");
                    fixture.placeBlock(new BlockPos(1, 3, 1), Blocks.AIR);
                    owl.makeFamiliarSearchesDue();
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(180L, () -> {
                try {
                    helper.assertTrue(owl.familiarState().home().isEmpty(), "a lost tree support releases the roost");
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
    // F9: the inherited zombie door goal, through the path that actually installs it
    // =================================================================================

    /**
     * {@code Zombie.finalizeSpawn} and {@code Zombie.readAdditionalSaveData} both call
     * {@code setCanBreakDoors}, which installs {@code breakDoorGoal} at priority 1 on any body
     * whose navigation is ground navigation. The cat and the toad are ground navigators. The
     * fixtures that construct bodies directly never call {@code finalizeSpawn}; the real
     * acquisition path for a brew-summoned toad is {@code EntityType.spawn}, which does.
     */
    public static void noFamiliarEverGainsADoorBreakingGoal(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            fixture.floor(0, 2, 0, 2);
            final FamiliarCatEntity cat = fixture.spawnCat(new BlockPos(0, 1, 0));
            final OwlEntity owl = fixture.spawnOwl(new BlockPos(1, 1, 0));
            final ToadEntity toad = fixture.spawnToad(new BlockPos(2, 1, 0));
            final Aggressor control = fixture.spawnAggressor(new BlockPos(1, 1, 2));
            control.setNoAi(true);

            // The control: the inherited setter really does install the goal on a ground zombie,
            // so "no BreakDoorGoal" below is a claim that can fail.
            control.setCanBreakDoors(true);
            helper.assertTrue(control.goalNames().contains("BreakDoorGoal"),
                "an ordinary ground zombie gains the door goal from this exact call");

            for (final AnimalFamiliarMob body : List.of(cat, owl, toad)) {
                // Both writers Zombie owns: the difficulty roll inside finalizeSpawn, and the
                // saved CanBreakDoors tag on load. Calling the setter directly is what both do.
                body.setCanBreakDoors(true);
                body.finalizeSpawn(
                    helper.getLevel(),
                    helper.getLevel().getCurrentDifficultyAt(body.blockPosition()),
                    EntitySpawnReason.EVENT,
                    null);
                helper.assertFalse(body.canBreakDoors(),
                    body.species() + " never accepts the door-breaking flag");
                helper.assertFalse(body.operationalGoalNames().contains("BreakDoorGoal"),
                    body.species() + " never gains a door goal at priority one");
                helper.assertValueEqual(body.operationalGoalNames().size(), 2,
                    body.species() + " keeps exactly its two declared goals");
            }

            helper.runAfterDelay(20L, () -> {
                try {
                    for (final AnimalFamiliarMob body : List.of(cat, owl, toad)) {
                        helper.assertValueEqual(body.operationalGoalNames().size(), 2,
                            body.species() + " still keeps exactly two goals after ticking");
                        helper.assertTrue(body.familiarCounters().decisions() > 0L,
                            body.species() + " ticked, so the goal count above means something");
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

    // =================================================================================
    // F10: the distinctness proof, against the production reach-ins rather than around them
    // =================================================================================

    /**
     * The five places a species reaches into the shared controller, each asked about the same
     * world and each giving three different answers.
     *
     * <p>This exists because the unit-level distinctness proof could not reach any of them. It was
     * a pure-rules simulation that re-implemented the species preconditions itself, so an auditor
     * made the OWL and TOAD profiles byte identical to the CAT's, replaced all five reach-ins with
     * the CAT's arm, and every one of its six cases still passed. Everything below calls production
     * directly; under that same reskin every block here fails.</p>
     */
    public static void theThreeSpeciesReachInsAreThreeDifferentQuestions(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ServerLevel level = helper.getLevel();
            fixture.floor(0, 2, 0, 2);
            final FamiliarCatEntity cat = fixture.spawnCat(new BlockPos(0, 1, 0));
            final OwlEntity owl = fixture.spawnOwl(new BlockPos(1, 1, 0));
            final ToadEntity toad = fixture.spawnToad(new BlockPos(2, 1, 0));

            // ---- reach-in 1: preyTag. Three tags, no two the same. ----
            helper.assertFalse(
                AnimalFamiliarRuntime.preyTag(AnimalFamiliarSpecies.CAT)
                    .equals(AnimalFamiliarRuntime.preyTag(AnimalFamiliarSpecies.OWL)),
                "a cat hunts vermin and an owl hunts its own quarry: not one tag");
            helper.assertFalse(
                AnimalFamiliarRuntime.preyTag(AnimalFamiliarSpecies.OWL)
                    .equals(AnimalFamiliarRuntime.preyTag(AnimalFamiliarSpecies.TOAD)),
                "an owl does not eat insects out of a toad's tag");
            helper.assertFalse(
                AnimalFamiliarRuntime.preyTag(AnimalFamiliarSpecies.CAT)
                    .equals(AnimalFamiliarRuntime.preyTag(AnimalFamiliarSpecies.TOAD)),
                "and a cat is not a toad");

            // ---- reach-in 2: footing. A ground dweller stands on something, a flyer hangs. ----
            fixture.placeBlock(new BlockPos(2, 3, 2), Blocks.OAK_LOG);
            final BlockPos onTheFloor = helper.absolutePos(new BlockPos(1, 1, 1));
            final BlockPos underTheLog = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.assertTrue(AnimalFamiliarRuntime.footing(AnimalFamiliarSpecies.CAT,
                    level, onTheFloor, oneRead(AnimalFamiliarSpecies.CAT), cat),
                "a cat's weight rests on the stone beneath it");
            helper.assertTrue(AnimalFamiliarRuntime.footing(AnimalFamiliarSpecies.TOAD,
                    level, onTheFloor, oneRead(AnimalFamiliarSpecies.TOAD), toad),
                "and so does a toad's");
            helper.assertFalse(AnimalFamiliarRuntime.footing(AnimalFamiliarSpecies.OWL,
                    level, onTheFloor, oneRead(AnimalFamiliarSpecies.OWL), owl),
                "an owl standing on that same stone has nothing to hang from");
            helper.assertTrue(AnimalFamiliarRuntime.footing(AnimalFamiliarSpecies.OWL,
                    level, underTheLog, oneRead(AnimalFamiliarSpecies.OWL), owl),
                "it hangs from the log above the airy position instead");
            helper.assertFalse(AnimalFamiliarRuntime.footing(AnimalFamiliarSpecies.CAT,
                    level, underTheLog, oneRead(AnimalFamiliarSpecies.CAT), cat),
                "and a cat there is standing on nothing, which is the whole difference");

            // ---- reach-in 3: qualifiesAsHome. Three positions, one species each. ----
            // The cat's household: beside a hay bale.
            fixture.placeBlock(new BlockPos(3, 1, 1), Blocks.HAY_BLOCK);
            final BlockPos besideTheHay = helper.absolutePos(new BlockPos(2, 1, 1));
            helper.assertTrue(AnimalFamiliarRuntime.qualifiesAsHome(AnimalFamiliarSpecies.CAT,
                    level, besideTheHay, oneRead(AnimalFamiliarSpecies.CAT), cat),
                "a household is a place beside a hay bale");
            helper.assertFalse(AnimalFamiliarRuntime.qualifiesAsHome(AnimalFamiliarSpecies.OWL,
                    level, besideTheHay, oneRead(AnimalFamiliarSpecies.OWL), owl),
                "a hay bale to one side is not a perch: an owl needs tagged support overhead");
            helper.assertFalse(AnimalFamiliarRuntime.qualifiesAsHome(AnimalFamiliarSpecies.TOAD,
                    level, besideTheHay, oneRead(AnimalFamiliarSpecies.TOAD), toad),
                "and it is not a shelter either: a toad needs cover overhead and water in reach");

            // The owl's perch: tagged support overhead, and real air beneath.
            helper.assertTrue(AnimalFamiliarRuntime.qualifiesAsHome(AnimalFamiliarSpecies.OWL,
                    level, underTheLog, oneRead(AnimalFamiliarSpecies.OWL), owl),
                "tagged support overhead with clear air beneath it is a perch");
            helper.assertFalse(AnimalFamiliarRuntime.qualifiesAsHome(AnimalFamiliarSpecies.CAT,
                    level, underTheLog, oneRead(AnimalFamiliarSpecies.CAT), cat),
                "a log two blocks up is not a household");
            helper.assertFalse(AnimalFamiliarRuntime.qualifiesAsHome(AnimalFamiliarSpecies.TOAD,
                    level, underTheLog, oneRead(AnimalFamiliarSpecies.TOAD), toad),
                "and with no water anywhere near it, it is not a shelter");

            // The toad's shelter: cover overhead AND standing water in reach. The leaves are in the
            // owl's perch tag as well as the toad's, on purpose, so the only thing separating the
            // two answers at this position is the clearance an owl needs and a toad does not.
            fixture.placeBlock(new BlockPos(1, 2, 2), Blocks.OAK_LEAVES);
            fixture.placeBlock(new BlockPos(0, 1, 2), Blocks.WATER);
            final BlockPos underTheLeaves = helper.absolutePos(new BlockPos(1, 1, 2));
            helper.assertTrue(AnimalFamiliarRuntime.qualifiesAsHome(AnimalFamiliarSpecies.TOAD,
                    level, underTheLeaves, oneRead(AnimalFamiliarSpecies.TOAD), toad),
                "cover overhead with standing water one block away is a shelter");
            helper.assertFalse(AnimalFamiliarRuntime.qualifiesAsHome(AnimalFamiliarSpecies.OWL,
                    level, underTheLeaves, oneRead(AnimalFamiliarSpecies.OWL), owl),
                "the same tagged block overhead is not a perch, because a perch at floor level with "
                    + "stone underneath is nothing to launch from");
            helper.assertFalse(AnimalFamiliarRuntime.qualifiesAsHome(AnimalFamiliarSpecies.CAT,
                    level, underTheLeaves, oneRead(AnimalFamiliarSpecies.CAT), cat),
                "and leaves over a puddle are still not a household");

            // ---- reach-in 4: insideSignatureEnvelope. One home, one quarry, three answers. ----
            final BlockPos perch = helper.absolutePos(new BlockPos(2, 2, 2));
            for (final AnimalFamiliarMob body : List.of(cat, owl, toad)) {
                body.setFamiliarState(body.familiarState().withHome(
                    Optional.of(perch),
                    Optional.of(level.dimension().identifier().toString())));
            }
            final Aggressor quarry = fixture.spawnAggressor(new BlockPos(2, 1, 1));
            quarry.setNoAi(true);
            final Optional<LivingEntity> noOwner = Optional.empty();
            helper.assertTrue(AnimalFamiliarRuntime.insideSignatureEnvelope(cat, level, quarry, noOwner),
                "a cat's envelope is its territory, and the quarry is inside it");
            helper.assertTrue(AnimalFamiliarRuntime.insideSignatureEnvelope(owl, level, quarry, noOwner),
                "an owl's envelope is what is below its perch, and the quarry is below it");
            helper.assertFalse(AnimalFamiliarRuntime.insideSignatureEnvelope(toad, level, quarry, noOwner),
                "a toad's envelope is a herb landmark, and there is no herb here");
            fixture.placeBlock(new BlockPos(1, 1, 1), Blocks.DANDELION);
            helper.assertTrue(AnimalFamiliarRuntime.insideSignatureEnvelope(toad, level, quarry, noOwner),
                "one flower beside the quarry, and only the toad's answer changes");
            quarry.snapTo(quarry.getX(), perch.getY() + 2.0D, quarry.getZ(), 0.0F, 0.0F);
            helper.assertFalse(AnimalFamiliarRuntime.insideSignatureEnvelope(owl, level, quarry, noOwner),
                "an owl does not pounce upward");
            helper.assertTrue(AnimalFamiliarRuntime.insideSignatureEnvelope(cat, level, quarry, noOwner),
                "and a cat's territory does not care about height");

            // ---- reach-in 5: pulseOwnerAura, through the real bound-owner path. ----
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(1, 1, 1));
            CreatureBehaviorState.bind(cat, owner.getUUID());
            CreatureBehaviorState.bind(toad, owner.getUUID());
            CreatureBehaviorState.bind(owl, owner.getUUID());

            helper.runAfterDelay(60L, () -> {
                try {
                    helper.assertTrue(owner.hasEffect(MobEffects.LUCK),
                        "a bound cat grants Luck, and no other species grants it");
                    helper.assertTrue(owner.hasEffect(MobEffects.WATER_BREATHING),
                        "a bound toad grants Water Breathing, which no cat has ever granted");
                    helper.assertTrue(owner.hasEffect(MobEffects.JUMP_BOOST),
                        "and Jump Boost with it");
                    helper.assertFalse(owner.hasEffect(MobEffects.SLOW_FALLING),
                        "the owl's aura is conditioned on a broom this owner is not carrying, so its "
                            + "absence is the owl answering its own question rather than the cat's");
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

    /** One position's worth of read allowance, from the production table. */
    private static ReadBudget oneRead(final AnimalFamiliarSpecies species) {
        return ReadBudget.of(AnimalFamiliarRules.homeReadsPerPosition(species));
    }

    // ---- fixture plumbing ----

    /** The arena's disposable hostile body. Not a familiar, not owned, and legal to defend against. */
    private static final class Aggressor extends net.minecraft.world.entity.monster.zombie.Zombie {
        private Aggressor(final ServerLevel level) {
            super(net.minecraft.world.entity.EntityTypes.ZOMBIE, level);
        }

        /** The control's goal names. {@code goalSelector} is protected, so only a subclass can read it. */
        private List<String> goalNames() {
            return goalSelector.getAvailableGoals().stream()
                .map(goal -> goal.getGoal().getClass().getSimpleName())
                .toList();
        }
    }

    private static final class FixtureScope implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private final List<Runnable> cleanupActions = new ArrayList<>();
        private boolean closed;

        private FixtureScope(final GameTestHelper helper) {
            this.helper = helper;
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

        /**
         * Builds a body through its registered type rather than through its constructor, so the
         * "the registered id constructs the dedicated body" claim is about the registry. Calling
         * the constructor and then asserting the constructed class is what made the three original
         * assertions tautologies.
         */
        private <T extends AnimalFamiliarMob> T spawnRegistered(
            final String id,
            final Class<T> dedicated,
            final BlockPos position
        ) {
            final Entity created = ModEntities.ALL.get(id).get()
                .create(helper.getLevel(), EntitySpawnReason.EVENT);
            helper.assertTrue(created != null,
                "the registered warlockery:" + id + " must construct an entity at all");
            helper.assertValueEqual(created.getClass().getName(), dedicated.getName(),
                "the exact registered warlockery:" + id + " must construct the dedicated "
                    + dedicated.getSimpleName());
            return place(dedicated.cast(created), position);
        }

        private FamiliarCatEntity spawnCat(final BlockPos position) {
            return spawnRegistered("familiar_cat", FamiliarCatEntity.class, position);
        }

        private OwlEntity spawnOwl(final BlockPos position) {
            return spawnRegistered("owl", OwlEntity.class, position);
        }

        private ToadEntity spawnToad(final BlockPos position) {
            return spawnRegistered("toad", ToadEntity.class, position);
        }

        private Aggressor spawnAggressor(final BlockPos position) {
            return place(new Aggressor(helper.getLevel()), position);
        }

        /**
         * A solid floor across the given footprint at relative y zero. A cat and a toad both
         * require solid footing directly beneath the position they claim, so an arena whose bottom
         * layer is air has nothing either of them could ever claim.
         */
        private void floor(final int fromX, final int toX, final int fromZ, final int toZ) {
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    placeBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
        }

        private void placeBlock(final BlockPos position, final net.minecraft.world.level.block.Block block) {
            final BlockState previous = helper.getBlockState(position);
            helper.setBlock(position, block.defaultBlockState());
            cleanupActions.add(() -> helper.setBlock(position, previous));
        }

        private void clearTaggedHerbLandmarks(
            final BlockPos center, final int horizontalRadius, final int verticalRadius
        ) {
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int y = -verticalRadius; y <= verticalRadius; y++) {
                    for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                        final BlockPos position = center.offset(x, y, z);
                        if (helper.getBlockState(position).is(WarlockeryTags.Blocks.TOAD_HERB_LANDMARKS)) {
                            placeBlock(position, Blocks.AIR);
                        }
                    }
                }
            }
        }

        /**
         * Every block state in a relative box, recorded so the run can be compared against it.
         * This is the honest form of the claim the deleted {@code worldEdits} counter carried.
         */
        private Map<BlockPos, BlockState> snapshot(final BlockPos first, final BlockPos second) {
            final Map<BlockPos, BlockState> states = new LinkedHashMap<>();
            for (int x = first.getX(); x <= second.getX(); x++) {
                for (int y = first.getY(); y <= second.getY(); y++) {
                    for (int z = first.getZ(); z <= second.getZ(); z++) {
                        final BlockPos relative = new BlockPos(x, y, z);
                        states.put(relative, helper.getBlockState(relative));
                    }
                }
            }
            return Map.copyOf(states);
        }

        private void assertUnchanged(final Map<BlockPos, BlockState> before) {
            before.forEach((relative, state) -> helper.assertValueEqual(
                helper.getBlockState(relative), state,
                "no familiar places, breaks, grows or fertilises anything, and " + relative
                    + " proves it against the arena rather than against a counter"));
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
