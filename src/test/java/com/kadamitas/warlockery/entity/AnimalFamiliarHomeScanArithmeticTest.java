package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Profile;
import com.kadamitas.warlockery.entity.behavior.ScanEnvelope;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic the delivered home search got wrong, stated so it cannot be got wrong again.
 *
 * <p>The defect was a unit confusion, not a logic error. {@code ScanEnvelope.window} takes a count
 * of <em>positions</em>; the delivered runtime handed it the <em>read</em> cap, and then charged the
 * same number against a {@code ReadBudget}. Because one position really costs between five and
 * twenty reads, the budget was exhausted after {@code cap / cost} positions -- about thirteen of
 * ninety-six for the cat and six of a hundred and twenty-eight for the toad -- so the advertised
 * 11x11x5 and 11x11x7 envelopes collapsed to a radius of one to two, and the rotating page walked
 * over offsets the loop had already stopped reaching.</p>
 *
 * <p>Everything here is pure: {@link ScanEnvelope} and {@link Profile} are world free, so the
 * coverage claim is directly checkable without a server. What a position costs is checked against
 * the runtime's charging sites by the live fixtures, which assert the real
 * {@code homeBlockReads} counter against both of these bounds.</p>
 */
final class AnimalFamiliarHomeScanArithmeticTest {

    /**
     * The read cap the delivered cat profile carried, and which it also used as its window length.
     * Kept here as a historical constant so the regression this file pins stays legible.
     */
    private static final int DELIVERED_CAT_READ_CAP = 96;

    /** A household two blocks along one axis. Squared offset four, and the reason this file exists. */
    private static final BlockPos TWO_BLOCKS_AWAY = new BlockPos(2, 0, 0);

    @Test
    void theReadCapIsTheWindowTimesWhatAPositionActuallyCosts() {
        for (final AnimalFamiliarSpecies species : AnimalFamiliarSpecies.values()) {
            final Profile profile = AnimalFamiliarRules.profile(species);
            final int cost = AnimalFamiliarRules.homeReadsPerPosition(species);
            assertTrue(cost > 1,
                species + " charges more than one read per position, which is the whole point");
            assertEquals(profile.homePositionsPerScan() * cost, profile.homeReadCap(),
                species + " must budget every position in its window at what a position costs");
        }
    }

    @Test
    void theThreeHomePredicatesCostThreeDifferentAmountsBecauseTheyAskThreeThings() {
        final int cat = AnimalFamiliarRules.homeReadsPerPosition(AnimalFamiliarSpecies.CAT);
        final int owl = AnimalFamiliarRules.homeReadsPerPosition(AnimalFamiliarSpecies.OWL);
        final int toad = AnimalFamiliarRules.homeReadsPerPosition(AnimalFamiliarSpecies.TOAD);
        assertEquals(3, Set.of(cat, owl, toad).size(), "three predicates, three costs");
        assertTrue(owl < cat, "an owl asks about one block above and one below: the cheapest");
        assertTrue(cat < toad, "a toad probes sixteen positions for water: the most expensive");
    }

    /**
     * The whole window is walkable. A budget of {@code positions * worstCasePerPosition} cannot be
     * exhausted before the loop reaches the last offset, whatever the scene looks like, so an
     * exhausted budget can no longer silently truncate a scan.
     */
    @Test
    void theBudgetCannotRunOutBeforeTheWindowDoes() {
        for (final AnimalFamiliarSpecies species : AnimalFamiliarSpecies.values()) {
            final Profile profile = AnimalFamiliarRules.profile(species);
            final ScanEnvelope envelope =
                ScanEnvelope.of(profile.homeRadiusHorizontal(), profile.homeRadiusVertical());
            final List<BlockPos> window = envelope.window(profile.homePositionsPerScan(), 0);
            assertEquals(profile.homePositionsPerScan(), window.size(),
                species + " asks for a window of positions and must get exactly that many");
            final int worstCase =
                window.size() * AnimalFamiliarRules.homeReadsPerPosition(species);
            assertTrue(worstCase <= profile.homeReadCap(),
                species + " could spend " + worstCase + " reads on a window its budget caps at "
                    + profile.homeReadCap());
        }
    }

    /**
     * The fixed near anchor is present in every window, and it reaches past the innermost ring.
     *
     * <p>This is the pure-side statement of what the new live fixture asserts against an arena: a
     * household at squared offset four is inside the anchor, so it is found on the first scan rather
     * than after a rotation. The delivered sizing could not reach it at all.</p>
     */
    @Test
    void theAnchorAlwaysContainsTheOwnBlockAndReachesPastTheInnermostRing() {
        for (final AnimalFamiliarSpecies species : AnimalFamiliarSpecies.values()) {
            final Profile profile = AnimalFamiliarRules.profile(species);
            final ScanEnvelope envelope =
                ScanEnvelope.of(profile.homeRadiusHorizontal(), profile.homeRadiusVertical());
            final int positions = profile.homePositionsPerScan();
            final Set<BlockPos> anchor = new HashSet<>(
                envelope.window(positions, 0).subList(0, envelope.anchorSize(positions)));
            assertTrue(anchor.contains(BlockPos.ZERO),
                species + " must evaluate its own block on every single scan");
            assertTrue(anchor.contains(TWO_BLOCKS_AWAY),
                species + " must reach squared offset four on every scan, or a home two blocks "
                    + "away is only ever found by rotation");
            // The anchor is what rotation can never take away, so it has to be a real
            // neighbourhood rather than a handful of offsets.
            assertTrue(anchor.size() >= 24,
                species + " keeps only " + anchor.size() + " offsets fixed, which is a ring");
        }
    }

    /**
     * The regression itself, pinned. Sizing the window by the read cap truncates the scan to
     * {@code cap / cost} positions, and that prefix does not contain a household two blocks away.
     */
    @Test
    void sizingTheWindowByTheReadCapIsWhatTruncatedTheScanToTheInnermostRing() {
        final Profile cat = AnimalFamiliarRules.profile(AnimalFamiliarSpecies.CAT);
        final ScanEnvelope envelope =
            ScanEnvelope.of(cat.homeRadiusHorizontal(), cat.homeRadiusVertical());
        final int cost = AnimalFamiliarRules.homeReadsPerPosition(AnimalFamiliarSpecies.CAT);

        // What the delivered code did: window length and budget both the read cap.
        final List<BlockPos> deliveredWindow = envelope.window(DELIVERED_CAT_READ_CAP, 0);
        final int positionsTheBudgetActuallyReached = DELIVERED_CAT_READ_CAP / cost;
        assertTrue(positionsTheBudgetActuallyReached < deliveredWindow.size(),
            "the delivered budget could not pay for its own window: "
                + positionsTheBudgetActuallyReached + " of " + deliveredWindow.size());
        assertFalse(
            new HashSet<>(deliveredWindow.subList(0, positionsTheBudgetActuallyReached))
                .contains(TWO_BLOCKS_AWAY),
            "the delivered scan stopped before squared offset four, which is why no fixture that "
                + "pinned the mob's own block could ever have caught it");

        // What it does now.
        final List<BlockPos> window = envelope.window(cat.homePositionsPerScan(), 0);
        assertTrue(new HashSet<>(window).contains(TWO_BLOCKS_AWAY),
            "and the whole window is now paid for, so squared offset four is evaluated");
        assertEquals(cat.homePositionsPerScan() * cost, cat.homeReadCap(),
            "because the budget is the window priced at what a position costs");
    }

    /** Every scan still terminates, and the union of successive scans is still the whole envelope. */
    @Test
    void everySpeciesStillCoversItsWholeEnvelopeInABoundedNumberOfScans() {
        for (final AnimalFamiliarSpecies species : AnimalFamiliarSpecies.values()) {
            final Profile profile = AnimalFamiliarRules.profile(species);
            final ScanEnvelope envelope =
                ScanEnvelope.of(profile.homeRadiusHorizontal(), profile.homeRadiusVertical());
            final int positions = profile.homePositionsPerScan();
            final int scans = envelope.scansToCover(positions);
            assertTrue(scans > 0 && scans < 64,
                species + " needs " + scans + " scans to cover its envelope");

            final Set<BlockPos> seen = new HashSet<>();
            int cursor = 0;
            for (int scan = 0; scan < scans; scan++) {
                seen.addAll(envelope.window(positions, cursor));
                cursor = envelope.advanceCursor(positions, cursor);
            }
            assertEquals(envelope.size(), seen.size(),
                species + " left " + (envelope.size() - seen.size())
                    + " offsets of its advertised envelope unevaluated after a full rotation");
        }
    }
}
