package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Action;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Decision;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Facts;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Profile;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Reason;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The proof that a cat, an owl and a toad are three animals rather than one animal in three
 * textures.
 *
 * <h2>Why this file was rewritten</h2>
 *
 * <p>The version that shipped was a pure-rules simulation that <em>re-implemented the species
 * preconditions inside itself</em>, in a {@code switch (species)} in the test. Every difference it
 * observed was therefore a difference the test had written, not one production had. An auditor made
 * the OWL and TOAD tuning tables byte identical to the CAT's and replaced all five production
 * species reach-ins with the CAT's arm, and all six cases still passed. A distinctness proof that
 * survives a deliberate reskin is not a proof.</p>
 *
 * <p>The rule this file now keeps: <strong>nothing here branches on the species.</strong> There is
 * no {@code switch (species)} and no {@code if (species ==)} anywhere below. Every per-species input
 * comes out of production -- {@link AnimalFamiliarRules#profile},
 * {@link AnimalFamiliarRules#awake}, {@link AnimalFamiliarRules#beyondTether},
 * {@link AnimalFamiliarRules#permits} and {@link AnimalFamiliarRuntime#preyTag} -- so if the three
 * were flattened into one, the three traces would flatten with them and the divergence bounds below
 * would go to zero.</p>
 *
 * <p>The level-dependent reach-ins ({@code footing}, {@code qualifiesAsHome},
 * {@code insideSignatureEnvelope} and the owner aura) cannot be reached without a server, and
 * pretending otherwise is what produced the previous file. They are proved against a real arena by
 * {@code AnimalFamiliarGameTests.theThreeSpeciesReachInsAreThreeDifferentQuestions}, which asks each
 * of them one question about one world and asserts three different answers.</p>
 */
final class AnimalFamiliarIdentityTest {

    private static final int STEPS = 2_400;

    /**
     * How many of the {@value #STEPS} decisions two species must reach by a different <em>rung</em>
     * before this file will call them different animals. A plain "not equal" is satisfied by one
     * position out of two thousand four hundred, which is the sort of bound that passes against
     * noise; this one is not.
     */
    private static final int MINIMUM_DIVERGENT_DECISIONS = 200;

    @BeforeAll
    static void bootstrapRegistries() {
        // Self-sufficient on purpose. A filtered run of this class alone must bootstrap what it
        // needs rather than inheriting a neighbour's bootstrap.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * The world all three live in for the length of the run. Nothing here is per-species; the day
     * clock, the weather, the owner's distance and what is standing nearby are one script.
     */
    private record World(long dayTime, boolean raining, double ownerDistanceSquared,
                         boolean preyPresent, boolean ownerAttacked) {

        static World at(final int step) {
            return new World(
                Math.floorMod(step * 100L, 24_000L),
                step % 160 >= 120,
                switch (Math.floorMod(step, 300) / 100) {
                    // Inside every tether, outside the toad only, then outside all three. The
                    // owl works on the longest leash and the toad on the shortest, so one owner
                    // distance produces three different answers -- and it produces them through
                    // AnimalFamiliarRules.beyondTether rather than through anything written here.
                    case 0 -> 36.0;
                    case 1 -> 400.0;
                    default -> 900.0;
                },
                step % 40 >= 8,
                step == 300
            );
        }
    }

    /**
     * One species' own mutable schedule, advanced by the shared rules only.
     *
     * <p>Every number this class uses comes from {@link Profile}. It does not know which species it
     * is holding and it must not learn: the moment a branch here says "if this is the owl", the
     * file is back to proving its own text.</p>
     */
    private static final class Familiar {
        private final AnimalFamiliarSpecies species;
        private final Profile profile;
        private boolean homeClaimed;
        private long nextHomeSearchAt;
        private long signatureCooldownUntil;
        private long signatureEndsAt;
        private boolean signatureRunning;
        private int homeSearchAttempts;

        private Familiar(final AnimalFamiliarSpecies species) {
            this.species = species;
            this.profile = AnimalFamiliarRules.profile(species);
        }

        private Decision step(final int tick, final World world) {
            final long now = tick;
            if (signatureRunning && now >= signatureEndsAt) {
                signatureRunning = false;
                signatureCooldownUntil = now + profile.signatureCooldownTicks();
            }
            final boolean awake =
                AnimalFamiliarRules.awake(species, world.dayTime(), world.raining());
            final boolean offCooldown = now >= signatureCooldownUntil;
            final boolean preyQualified = !signatureRunning && offCooldown && world.preyPresent();
            final Facts facts = new Facts(
                species,
                false,
                true,
                world.ownerDistanceSquared(),
                world.ownerAttacked(),
                signatureRunning,
                offCooldown,
                preyQualified,
                homeClaimed,
                homeClaimed,
                homeClaimed,
                now >= nextHomeSearchAt,
                awake
            );
            final Decision decision = AnimalFamiliarRules.decide(facts);
            if (decision.reason() == Reason.PREY_QUALIFIED) {
                signatureRunning = true;
                signatureEndsAt = now + profile.telegraphTicks() * 3L;
            }
            if (decision.action() == Action.HOME_SEARCH) {
                homeSearchAttempts++;
                // The second due search finds something; the first does not. Same script for all
                // three, so any difference in when a home is claimed is the species' own cadence.
                final boolean qualified = homeSearchAttempts >= 2;
                homeClaimed = qualified;
                nextHomeSearchAt = AnimalFamiliarRules
                    .recordSearch(now, profile.homeSearchIntervalTicks(), qualified, 0)
                    .nextDueAt();
            }
            return decision;
        }
    }

    private static Map<AnimalFamiliarSpecies, List<Decision>> run() {
        final Map<AnimalFamiliarSpecies, List<Decision>> traces =
            new EnumMap<>(AnimalFamiliarSpecies.class);
        for (final AnimalFamiliarSpecies species : AnimalFamiliarSpecies.values()) {
            final Familiar familiar = new Familiar(species);
            final List<Decision> trace = new ArrayList<>(STEPS);
            for (int tick = 0; tick < STEPS; tick++) {
                trace.add(familiar.step(tick, World.at(tick)));
            }
            traces.put(species, List.copyOf(trace));
        }
        return traces;
    }

    private static List<Action> actions(final List<Decision> trace) {
        final List<Action> actions = new ArrayList<>(trace.size());
        trace.forEach(decision -> actions.add(decision.action()));
        return List.copyOf(actions);
    }

    private static List<Reason> reasons(final List<Decision> trace) {
        final List<Reason> reasons = new ArrayList<>(trace.size());
        trace.forEach(decision -> reasons.add(decision.reason()));
        return List.copyOf(reasons);
    }

    private static int divergentDecisions(final List<Reason> left, final List<Reason> right) {
        int divergent = 0;
        for (int index = 0; index < left.size(); index++) {
            if (left.get(index) != right.get(index)) {
                divergent++;
            }
        }
        return divergent;
    }

    /**
     * The case the reskin has to fail.
     *
     * <p>Reasons come from one shared enum, so two reskins with different action <em>names</em>
     * still produce identical reason traces. The action-trace comparison this file used to carry
     * was therefore satisfied by three different words; it is gone, and the count below has taken
     * its place. Flatten the three tuning tables into one and this goes to zero for all three
     * pairs.</p>
     */
    @Test
    void theThreeTakeDifferentRungsOnDifferentTicksAndNotMerelyDifferentWords() {
        final var traces = run();
        for (final AnimalFamiliarSpecies left : AnimalFamiliarSpecies.values()) {
            for (final AnimalFamiliarSpecies right : AnimalFamiliarSpecies.values()) {
                if (left.compareTo(right) >= 0) {
                    continue;
                }
                final int divergent = divergentDecisions(
                    reasons(traces.get(left)), reasons(traces.get(right)));
                assertTrue(divergent >= MINIMUM_DIVERGENT_DECISIONS,
                    left + " and " + right + " took a different rung on only " + divergent
                        + " of " + STEPS + " decisions, which is not a different animal");
                assertNotEquals(reasons(traces.get(left)), reasons(traces.get(right)),
                    left + " and " + right + " must take different rungs, not renamed ones");
            }
        }
    }

    /**
     * The waking window, straight out of production, on one world. At midday in the clear only the
     * cat is up; at midday in rain the cat and the toad are; at night the owl and the toad are.
     */
    @Test
    void theThreeWakingWindowsDisagreeOnTheSameSkyWithoutTheTestKnowingWhy() {
        final long midday = 6_000L;
        final long midnight = 18_000L;
        final Map<AnimalFamiliarSpecies, String> signature =
            new EnumMap<>(AnimalFamiliarSpecies.class);
        for (final AnimalFamiliarSpecies species : AnimalFamiliarSpecies.values()) {
            signature.put(species, ""
                + AnimalFamiliarRules.awake(species, midday, false)
                + AnimalFamiliarRules.awake(species, midday, true)
                + AnimalFamiliarRules.awake(species, midnight, false));
        }
        assertEquals(3, java.util.Set.copyOf(signature.values()).size(),
            "three species, three different answers to the same three skies, but got " + signature);
    }

    /** The tether, straight out of production. One owner distance, three different answers. */
    @Test
    void oneOwnerDistanceProducesThreeDifferentTetherAnswers() {
        final double betweenToadAndCat = 100.0;
        final double betweenCatAndOwl = 400.0;
        final Map<AnimalFamiliarSpecies, String> signature =
            new EnumMap<>(AnimalFamiliarSpecies.class);
        for (final AnimalFamiliarSpecies species : AnimalFamiliarSpecies.values()) {
            signature.put(species, ""
                + AnimalFamiliarRules.beyondTether(species, true, betweenToadAndCat)
                + AnimalFamiliarRules.beyondTether(species, true, betweenCatAndOwl));
        }
        assertEquals(3, java.util.Set.copyOf(signature.values()).size(),
            "the three leashes must be three lengths, but got " + signature);
    }

    /**
     * A production species reach-in, called for real. {@code AnimalFamiliarRuntime.preyTag} is the
     * one of the five that needs no level, so it is asserted here; the other four are asserted
     * against a real arena in the live fixture named in this class's javadoc.
     */
    @Test
    void theThreeHuntThreeDifferentTagsThroughTheProductionReachIn() {
        final var cat = AnimalFamiliarRuntime.preyTag(AnimalFamiliarSpecies.CAT);
        final var owl = AnimalFamiliarRuntime.preyTag(AnimalFamiliarSpecies.OWL);
        final var toad = AnimalFamiliarRuntime.preyTag(AnimalFamiliarSpecies.TOAD);
        assertNotEquals(cat, owl, "a cat's vermin is not an owl's quarry");
        assertNotEquals(owl, toad, "an owl's quarry is not a toad's insect");
        assertNotEquals(cat, toad, "and a cat's vermin is not a toad's insect");
        assertEquals(3, java.util.Set.of(cat, owl, toad).size(),
            "three species, three prey tags");
    }

    @Test
    void noSpeciesEverSchedulesAnotherSpeciesAction() {
        final var traces = run();
        traces.forEach((species, trace) -> trace.forEach(decision ->
            assertTrue(AnimalFamiliarRules.permits(species, decision.action()),
                species + " emitted " + decision.action() + ", which is not in its vocabulary")));
    }

    @Test
    void eachSpeciesActuallyReachesItsOwnSignatureRestAndActiveActionInTheRun() {
        final var traces = run();
        traces.forEach((species, trace) -> {
            final Profile profile = AnimalFamiliarRules.profile(species);
            final List<Action> actions = actions(trace);
            assertTrue(actions.contains(profile.signatureAction()),
                species + " never reached its signature action, so the run proves nothing about it");
            assertTrue(actions.contains(profile.activeAction()),
                species + " never reached its active action");
            assertTrue(actions.contains(profile.restAction()),
                species + " never reached its rest action");
            assertFalse(actions.isEmpty(), species + " produced no decisions at all");
        });
    }

    @Test
    void theSharedRungsAreReachedByAllThreeBecauseTheLadderIsGenuinelyOneLadder() {
        final var traces = run();
        traces.forEach((species, trace) -> {
            final List<Action> actions = actions(trace);
            assertTrue(actions.contains(Action.DEFEND_OWNER),
                species + " must answer the shared owner-defence rung");
            assertTrue(actions.contains(Action.TETHER_RETURN),
                species + " must answer the shared tether rung");
            assertTrue(actions.contains(Action.HOME_SEARCH),
                species + " must answer the shared home-search rung");
        });
    }

    @Test
    void theRunIsDeterministicSoAFailureIsReproducible() {
        assertEquals(run(), run());
    }
}
