package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.SpectralSteedRules.Gait;
import com.kadamitas.warlockery.entity.SpectralSteedRules.RestSearchOutcome;
import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.ScanEnvelope;
import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Scope item 14, which was never written.
 *
 * <p>It covers the parts of {@link SpectralSteedRuntime}'s behaviour that are decided before any
 * level is touched: the exact scan parameterisation this family instantiates, the read allowances
 * it declares, what one bounded search is allowed to conclude, and whether every counter an
 * assertion anywhere reads has something that actually moves it.</p>
 *
 * <p>Three of these were previously carried by hand trace alone. The generic
 * {@code ScanEnvelopeCoverageTest} proves the windowing primitive over a grid of shapes, but no
 * test named this family's own {@code (2, 1)} envelope with a sixteen-read window, and no fixture
 * ever exercised the rotating half of that window: the live case puts its hay at offset
 * {@code (+1, 0, +1)}, which lands inside the very first page, so one search finds it and the
 * rotation never has to happen.</p>
 */
final class SpectralSteedRuntimeTest {

    /** Exactly what {@code pursueRest} builds. */
    private static final ScanEnvelope ENVELOPE = ScanEnvelope.of(
        SpectralSteedRules.REST_HORIZONTAL_RADIUS, SpectralSteedRules.REST_VERTICAL_RADIUS
    );
    private static final int WINDOW = SpectralSteedRules.MAX_REST_CANDIDATES;

    /** Where the live fixture puts its hay, relative to the steed. */
    private static final BlockPos FIXTURE_LANDMARK = new BlockPos(1, 0, 1);

    /** A corner of the declared envelope: the farthest offset the search is ever allowed to reach. */
    private static final BlockPos FAR_CORNER = new BlockPos(
        SpectralSteedRules.REST_HORIZONTAL_RADIUS,
        SpectralSteedRules.REST_VERTICAL_RADIUS,
        SpectralSteedRules.REST_HORIZONTAL_RADIUS
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ------------------------------------------------ the family's own parameterisation

    @Test
    void thisFamilysEnvelopeIsTheBoxItDeclaresAndSplitsIntoAFixedAnchorAndARotatingPage() {
        assertEquals(5 * 5 * 3, ENVELOPE.size(), "radius two horizontally and one vertically");
        assertEquals(WINDOW / 2, ENVELOPE.anchorSize(WINDOW),
            "half the window is the anchor that never rotates away");
        assertEquals(WINDOW - ENVELOPE.anchorSize(WINDOW), ENVELOPE.pageSize(WINDOW),
            "and the other half is the page that does");
        assertEquals(ENVELOPE.size() - ENVELOPE.anchorSize(WINDOW), ENVELOPE.tailSize(WINDOW));
        assertEquals(WINDOW, ENVELOPE.window(WINDOW, 0).size(),
            "one search evaluates exactly the candidates it is budgeted for");
        assertEquals(
            Math.ceilDiv(ENVELOPE.tailSize(WINDOW), ENVELOPE.pageSize(WINDOW)),
            ENVELOPE.scansToCover(WINDOW)
        );
        assertEquals(9, ENVELOPE.scansToCover(WINDOW),
            "nine searches, three minutes of a steed's life at the declared interval");
    }

    /**
     * The record correction. The live fixture's landmark is not in the fixed anchor, as was
     * reported: at squared distance two it sorts to an index past the anchor and is reached by the
     * rotating page on its very first, unrotated position. That is why the fixture passes on one
     * search, and equally why the fixture proves nothing at all about rotation.
     */
    @Test
    void theFixtureLandmarkSitsInTheFirstPageRatherThanInTheFixedAnchor() {
        final int index = ENVELOPE.offsets().indexOf(FIXTURE_LANDMARK);
        assertNotEquals(-1, index, "the fixture offset must be inside the declared envelope");
        assertTrue(index >= ENVELOPE.anchorSize(WINDOW),
            "it is past the anchor, so it is page-reached and not anchor-reached, it sorts to "
                + index + " against an anchor of " + ENVELOPE.anchorSize(WINDOW));
        assertTrue(ENVELOPE.window(WINDOW, 0).contains(FIXTURE_LANDMARK),
            "the unrotated page still covers it, which is why one search is enough there");
        assertFalse(ENVELOPE.window(WINDOW, ENVELOPE.advanceCursor(WINDOW, 0))
                .contains(FIXTURE_LANDMARK),
            "and the very next page does not, which is the property no fixture exercises");
    }

    /**
     * The rotation this family's numbers actually require, proved against the truncated walk it
     * replaces. A steed that re-read the first sixteen offsets every time would never evaluate the
     * corner of its own declared envelope, however long it stood there.
     */
    @Test
    void redATruncatedWindowNeverReachesTheCornerThatTheRotatingOneCoversInNineSearches() {
        final Set<BlockPos> truncated = new HashSet<>(ENVELOPE.offsets().subList(0, WINDOW));
        assertFalse(truncated.contains(FAR_CORNER),
            "the defective walk, restated with this family's own numbers");

        final Set<BlockPos> rotated = new LinkedHashSet<>();
        int cursor = 0;
        for (int search = 0; search < ENVELOPE.scansToCover(WINDOW); search++) {
            rotated.addAll(ENVELOPE.window(WINDOW, cursor));
            cursor = ENVELOPE.advanceCursor(WINDOW, cursor);
        }
        assertTrue(rotated.contains(FAR_CORNER), "the rotating one reaches it");
        assertEquals(ENVELOPE.size(), rotated.size(), "and everything else besides");
        // The page does not divide the tail, so the ninth window overlaps the first rather than
        // landing back on it. That overlap is the reason coverage needs the ceiling of the division
        // and not the division: eight searches would leave three offsets of the tail unread.
        assertEquals(
            Math.floorMod(ENVELOPE.scansToCover(WINDOW) * ENVELOPE.pageSize(WINDOW),
                ENVELOPE.tailSize(WINDOW)),
            cursor,
            "the cursor lands past its start because the page does not divide the tail"
        );
        assertNotEquals(0, ENVELOPE.tailSize(WINDOW) % ENVELOPE.pageSize(WINDOW),
            "which is the premise of the assertion above");

        final Set<BlockPos> eightSearches = new LinkedHashSet<>();
        int shortCursor = 0;
        for (int search = 0; search < ENVELOPE.scansToCover(WINDOW) - 1; search++) {
            eightSearches.addAll(ENVELOPE.window(WINDOW, shortCursor));
            shortCursor = ENVELOPE.advanceCursor(WINDOW, shortCursor);
        }
        assertTrue(eightSearches.size() < ENVELOPE.size(),
            "one search short of the declared count really is short, so the count is not padded");
    }

    /**
     * The advance is unconditional in {@code pursueRest} rather than inside the candidate loop,
     * which is what stops the rotation stalling on a page whose contents were never usable. If the
     * cursor only moved on a successful search, a steed in unusable near terrain would re-read the
     * same page forever, which is the innermost-ring defect this envelope exists to prevent.
     */
    @Test
    void everySearchMovesTheCursorByOneWholePageWhateverItFound() {
        int cursor = 0;
        final Set<Integer> visited = new LinkedHashSet<>();
        for (int search = 0; search < ENVELOPE.scansToCover(WINDOW); search++) {
            assertTrue(visited.add(cursor), "no page is revisited before the envelope is covered");
            final int next = ENVELOPE.advanceCursor(WINDOW, cursor);
            assertNotEquals(cursor, next, "a search that found nothing still moves the window");
            cursor = next;
        }
        assertEquals(ENVELOPE.scansToCover(WINDOW), visited.size());
    }

    // ----------------------------------------------------------- the read allowances

    /**
     * Both empty outcomes are reachable at this family's numbers, so neither is decoration. A
     * window every one of whose candidates is rejected on its first read costs one read each and
     * fits inside the allowance, which is what {@code NOTHING_QUALIFIED} means. A window whose
     * candidates are all landmarks with blocked stances costs far more than the allowance, which is
     * what {@code BUDGET_EXHAUSTED} means.
     */
    @Test
    void theSearchAllowanceIsBigEnoughToWalkAWindowAndSmallEnoughToRunOutOnADenseOne() {
        final int cheapestWholeWindow = WINDOW;
        final int fullyProvedStance = 1 + 4 + 3 + 1 + 1;
        final int dearestCandidate = 1 + fullyProvedStance * horizontalDirections();
        final int dearestWholeWindow = WINDOW * dearestCandidate;

        assertTrue(cheapestWholeWindow <= SpectralSteedRules.MAX_REST_BLOCK_READS,
            "an empty window can always be walked to its end, so absence is knowable");
        assertTrue(SpectralSteedRules.MAX_REST_BLOCK_READS < dearestWholeWindow,
            "and a dense one cannot, so exhaustion is a real outcome and not a decorative arm: "
                + SpectralSteedRules.MAX_REST_BLOCK_READS + " against " + dearestWholeWindow);
    }

    /**
     * The allowance the held-site re-check declares, shown to be exactly its own worst case. Padded,
     * it would not bind; short, it would release good sites at random.
     */
    @Test
    void theHeldSiteRecheckAllowanceIsTightAgainstTheChecksItActuallyPerforms() {
        final ReadBudget budget = ReadBudget.of(SpectralSteedRules.MAX_REST_VALIDATION_READS);
        assertTrue(budget.charge(), "world border");
        for (int corner = 0; corner < 4; corner++) {
            assertTrue(budget.charge(), "loaded footprint corner " + corner);
        }
        assertTrue(budget.charge(), "feet block state");
        assertTrue(budget.charge(), "head block state");
        assertTrue(budget.charge(), "support block state");
        assertTrue(budget.charge(), "block collision query");
        assertTrue(budget.charge(), "entity collision query");
        int looks = 0;
        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            assertTrue(budget.charge(), "the allowance must cover the landmark look at " + direction);
            looks++;
        }
        assertEquals(4, looks);
        assertTrue(budget.exhausted(), "nothing is left over, so the cap is tight rather than padded");
        assertFalse(budget.charge(),
            "and a further read is refused, which releases the site instead of trusting it");
        assertEquals(SpectralSteedRules.MAX_REST_VALIDATION_READS, budget.spent());
        assertTrue(SpectralSteedRules.MAX_REST_VALIDATION_READS
            < SpectralSteedRules.MAX_REST_BLOCK_READS,
            "re-checking one site is cheaper than searching for a new one");
    }

    @Test
    void aRefusedWorldQueryNeverExecutesItsReader() {
        final ReadBudget budget = ReadBudget.of(SpectralSteedRules.MAX_REST_BLOCK_READS);
        final AtomicInteger executed = new AtomicInteger();
        while (budget.read(() -> executed.incrementAndGet()).isPresent()) {
            // Exhaust the exact production allowance through its charge-before-query API.
        }
        assertEquals(SpectralSteedRules.MAX_REST_BLOCK_READS, budget.spent());
        assertEquals(SpectralSteedRules.MAX_REST_BLOCK_READS, executed.get());
        assertTrue(budget.read(() -> executed.incrementAndGet()).isEmpty());
        assertEquals(SpectralSteedRules.MAX_REST_BLOCK_READS, executed.get(),
            "a refused query must not reach the world reader");
    }

    // ------------------------------------------- exhaustion is cost, absence is absence

    /**
     * The defect, reconstructed and then removed. Before the fix the search had one empty return
     * for both empty outcomes and the caller took {@code failed} on it, so three looks that merely
     * ran out of reads opened the backoff window with nothing in the world having changed. A steed
     * in a dense hay meadow, which is the best rest terrain there is, was the case that hit it.
     */
    @Test
    void redThreeExhaustedLooksUsedToOpenTheBackoffWindowAndNowDoNot() {
        RouteRequest asShipped = fresh();
        RouteRequest asFixed = fresh();
        for (int look = 0; look < SpectralSteedRules.REST_BACKOFF.failuresBeforeBackoff(); look++) {
            asShipped = asShipped.failed(SpectralSteedRules.REST_BACKOFF);
            asFixed = SpectralSteedRules.afterRestSearch(asFixed, RestSearchOutcome.BUDGET_EXHAUSTED);
        }

        assertEquals(3, asShipped.consecutiveFailures(), "the shape this fix removes");
        assertEquals(SpectralSteedRules.REST_BACKOFF.baseTicks(), asShipped.backoffRemaining(),
            "cost alone used to buy a hundred ticks of not looking");

        assertEquals(0, asFixed.consecutiveFailures(),
            "a look stopped by its own cost has learned nothing about the world");
        assertEquals(0, asFixed.backoffRemaining(), "so it opens no window");
        assertFalse(asFixed.mayRequest(),
            "but the cadence is armed either way, so the sweep is still paced rather than repeated");
    }

    @Test
    void anEvaluatedEmptyWindowIsStillAFailureAndStillReachesTheBackoffWindow() {
        RouteRequest request = fresh();
        for (int look = 0; look < SpectralSteedRules.REST_BACKOFF.failuresBeforeBackoff(); look++) {
            assertEquals(0, request.backoffRemaining(), "the window opens on the third and no sooner");
            request = SpectralSteedRules.afterRestSearch(request, RestSearchOutcome.NOTHING_QUALIFIED);
        }
        assertEquals(3, request.consecutiveFailures(), "absence really is evidence");
        assertEquals(SpectralSteedRules.REST_BACKOFF.baseTicks(), request.backoffRemaining());
    }

    @Test
    void aFindClearsTheFailureRunAndWhateverWindowItHadOpened() {
        RouteRequest request = fresh()
            .failed(SpectralSteedRules.REST_BACKOFF)
            .failed(SpectralSteedRules.REST_BACKOFF)
            .failed(SpectralSteedRules.REST_BACKOFF);
        assertTrue(request.backoffRemaining() > 0, "the case needs a genuinely open window");

        request = SpectralSteedRules.afterRestSearch(request, RestSearchOutcome.FOUND);
        assertEquals(0, request.consecutiveFailures());
        assertEquals(0, request.backoffRemaining());
    }

    /**
     * An exhausted look must never be able to reach the threshold on its own, however many times it
     * happens. This is the property that keeps a steed out of backoff for standing somewhere
     * expensive to look at, and it is stated over more looks than the threshold needs.
     */
    @Test
    void noNumberOfExhaustedLooksEverReachesTheBackoffThreshold() {
        RouteRequest request = fresh();
        for (int look = 0; look < 10 * SpectralSteedRules.REST_BACKOFF.failuresBeforeBackoff(); look++) {
            request = SpectralSteedRules.afterRestSearch(request, RestSearchOutcome.BUDGET_EXHAUSTED);
            assertEquals(0, request.backoffRemaining(), "look " + look);
        }
        assertFalse(SpectralSteedRules.REST_BACKOFF.engagedAt(request.consecutiveFailures()));
    }

    @Test
    void everyOutcomeArmsTheCadenceAndAFourthWouldHaveToDecideWhatItDoes() {
        for (final RestSearchOutcome outcome : RestSearchOutcome.values()) {
            final RouteRequest after = SpectralSteedRules.afterRestSearch(fresh(), outcome);
            assertEquals(0, after.cadence().sinceLast(),
                "a sweep that ran must not be offered again next tick, whatever it found: " + outcome);
        }
        assertEquals(3, RestSearchOutcome.values().length,
            "a fourth outcome must come with a decision about the failure run, not inherit one");
    }

    // ------------------------------------------------------------- the counter audit

    /**
     * Every counter this family declares, checked mechanically for something that actually moves
     * it. The failure this catches is a counter that assertions read ten times and nothing anywhere
     * increments, which reads as a passing bound and is really an unfalsifiable one.
     */
    @Test
    void everyDeclaredCounterHasAWithErThatMovesItAndMovesNothingElse() {
        final SpectralSteedState empty = SpectralSteedState.empty();
        final Set<String> declared = countersOf(empty).keySet();
        assertEquals(Set.of("restSearches", "restBlockReads", "restValidationReads",
            "restNavigationStarts", "restsCompleted", "balks", "gaitChanges", "bondGains",
            "warningVisits", "warningTelegraphs", "warningsIssued"), declared);
        for (final Long value : countersOf(empty).values()) {
            assertEquals(0L, value.longValue(), "a fresh steed has done no work");
        }

        final Map<String, Set<String>> moves = new LinkedHashMap<>();
        moves.put("withRestSearchCharged",
            moved(empty, empty.withRestSearchCharged(SpectralSteedRules.MAX_REST_BLOCK_READS, 8)));
        moves.put("withRestValidationCharged", moved(empty,
            empty.withRestValidationCharged(SpectralSteedRules.MAX_REST_VALIDATION_READS)));
        moves.put("withRestNavigationStarted", moved(empty, empty.withRestNavigationStarted()));
        moves.put("withRestCompleted", moved(empty, empty.withRestCompleted()));
        moves.put("startingBalk", moved(empty, empty.startingBalk(10)));
        moves.put("withGait", moved(empty, empty.withGait(Gait.WALK)));
        moves.put("withBondGain", moved(empty, empty.withBondGain(1, true)));
        moves.put("withWarningIssued(3, 1)", moved(empty, empty.withWarningIssued(3, 1)));
        moves.put("withWarningIssued(3, 0)", moved(empty, empty.withWarningIssued(3, 0)));

        assertEquals(Set.of("restSearches", "restBlockReads"), moves.get("withRestSearchCharged"));
        assertEquals(Set.of("restValidationReads"), moves.get("withRestValidationCharged"));
        assertEquals(Set.of("restNavigationStarts"), moves.get("withRestNavigationStarted"));
        assertEquals(Set.of("restsCompleted"), moves.get("withRestCompleted"));
        assertEquals(Set.of("balks"), moves.get("startingBalk"));
        assertEquals(Set.of("gaitChanges"), moves.get("withGait"));
        assertEquals(Set.of("bondGains"), moves.get("withBondGain"));
        assertEquals(Set.of("warningVisits", "warningTelegraphs", "warningsIssued"),
            moves.get("withWarningIssued(3, 1)"));
        assertEquals(Set.of("warningVisits"), moves.get("withWarningIssued(3, 0)"),
            "a warning that reached nobody is not a warning issued");

        final Set<String> everMoved = new LinkedHashSet<>();
        moves.values().forEach(everMoved::addAll);
        assertEquals(declared, everMoved,
            "no counter is declared that nothing anywhere increments");
    }

    /** A band that did not move is not a change, so the counter cannot be inflated by re-applying. */
    @Test
    void aCounterOnlyMovesWhenTheThingItCountsActuallyHappened() {
        final SpectralSteedState empty = SpectralSteedState.empty();
        assertEquals(Set.of(), moved(empty, empty.withGait(empty.gait())));
        assertEquals(Set.of(), moved(empty, empty.withBondGain(0, true)));
        assertEquals(Set.of(), moved(empty, empty.withRestValidationCharged(0)),
            "a re-check that read nothing charges nothing");
    }

    // --------------------------------------------------------------------- helpers

    private static RouteRequest fresh() {
        return RouteRequest.every(SpectralSteedRules.REST_SEARCH_INTERVAL_TICKS);
    }

    private static int horizontalDirections() {
        int count = 0;
        for (final Direction ignored : Direction.Plane.HORIZONTAL) {
            count++;
        }
        return count;
    }

    private static Set<String> moved(final SpectralSteedState before, final SpectralSteedState after) {
        final Map<String, Long> start = countersOf(before);
        final Map<String, Long> end = countersOf(after);
        final Set<String> changed = new LinkedHashSet<>();
        for (final Map.Entry<String, Long> entry : start.entrySet()) {
            if (!entry.getValue().equals(end.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }

    private static Map<String, Long> countersOf(final SpectralSteedState state) {
        final Map<String, Long> values = new LinkedHashMap<>();
        final List<RecordComponent> components =
            List.of(SpectralSteedState.Counters.class.getRecordComponents());
        for (final RecordComponent component : components) {
            try {
                values.put(component.getName(),
                    (Long) component.getAccessor().invoke(state.counters()));
            } catch (final ReflectiveOperationException failure) {
                throw new AssertionError("counter " + component.getName() + " is unreadable", failure);
            }
        }
        return values;
    }
}
