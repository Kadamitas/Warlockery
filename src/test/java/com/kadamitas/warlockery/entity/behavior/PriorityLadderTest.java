package com.kadamitas.warlockery.entity.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PriorityLadderTest {

    /** A family's own ladder: conditions first, then phases, exactly as the four families rank. */
    private enum Concern { HAZARD, DEFENCE, BINDING, DEFEND, WARN, ATTEND, WANDER }

    private static final PriorityLadder<Concern> LADDER = PriorityLadder.ofEnum(Concern.class);

    @Test
    void declarationOrderIsPriorityOrderAndCoversEveryConstant() {
        assertEquals(0, LADDER.rank(Concern.HAZARD));
        assertEquals(6, LADDER.rank(Concern.WANDER));
        assertEquals(List.of(Concern.values()), LADDER.order());
        for (final Concern concern : Concern.values()) {
            LADDER.rank(concern);
        }
    }

    @Test
    void hazardOutranksCombatOutranksRoutine() {
        assertTrue(LADDER.outranks(Concern.HAZARD, Concern.DEFEND));
        assertTrue(LADDER.outranks(Concern.DEFEND, Concern.WANDER));
        assertFalse(LADDER.outranks(Concern.WANDER, Concern.HAZARD));
        assertFalse(LADDER.outranks(Concern.HAZARD, Concern.HAZARD));
    }

    @Test
    void theMostUrgentActiveConcernWins() {
        assertEquals(Optional.of(Concern.HAZARD),
            LADDER.mostUrgent(List.of(Concern.WANDER, Concern.HAZARD, Concern.ATTEND)));
        assertEquals(Optional.empty(), LADDER.mostUrgent(List.of()));
    }

    @Test
    void selectRunsNothingAtAll() {
        final List<String> ran = new ArrayList<>();
        final List<PriorityLadder.Rung<Concern, String>> rungs = List.of(
            new PriorityLadder.Rung<>(Concern.WANDER, _ -> true, ran::add),
            new PriorityLadder.Rung<>(Concern.HAZARD, _ -> true, ran::add));
        final Optional<PriorityLadder.Rung<Concern, String>> chosen = LADDER.select(rungs, "tick");
        assertEquals(Optional.of(Concern.HAZARD), chosen.map(PriorityLadder.Rung::concern));
        assertTrue(ran.isEmpty(),
            "select answers the ranking question and leaves dispatch to the family");
    }

    @Test
    void rungsAreConsideredInLadderOrderRatherThanRegistrationOrder() {
        final List<String> ran = new ArrayList<>();
        final List<PriorityLadder.Rung<Concern, String>> rungs = List.of(
            new PriorityLadder.Rung<>(Concern.WANDER, _ -> true, _ -> ran.add("wander")),
            new PriorityLadder.Rung<>(Concern.ATTEND, _ -> true, _ -> ran.add("attend")),
            new PriorityLadder.Rung<>(Concern.HAZARD, _ -> true, _ -> ran.add("hazard")));
        assertEquals(Optional.of(Concern.HAZARD), LADDER.dispatch(rungs, "tick"));
        assertEquals(List.of("hazard"), ran, "exactly one rung runs, the most urgent that applies");
    }

    @Test
    void anInapplicableUrgentRungYieldsToTheNextOneDown() {
        final List<String> ran = new ArrayList<>();
        final List<PriorityLadder.Rung<Concern, String>> rungs = List.of(
            new PriorityLadder.Rung<>(Concern.HAZARD, _ -> false, _ -> ran.add("hazard")),
            new PriorityLadder.Rung<>(Concern.DEFEND, _ -> false, _ -> ran.add("defend")),
            new PriorityLadder.Rung<>(Concern.WANDER, _ -> true, _ -> ran.add("wander")));
        assertEquals(Optional.of(Concern.WANDER), LADDER.dispatch(rungs, "tick"));
        assertEquals(List.of("wander"), ran);
    }

    @Test
    void nothingApplyingDispatchesNothing() {
        final List<PriorityLadder.Rung<Concern, String>> rungs = List.of(
            new PriorityLadder.Rung<>(Concern.HAZARD, _ -> false, _ -> { }));
        assertEquals(Optional.empty(), LADDER.dispatch(rungs, "tick"));
    }

    @Test
    void afamilyMayRankASubsetExplicitlyWithoutUsingAnEnum() {
        final PriorityLadder<String> ladder = PriorityLadder.of(List.of("hazard", "combat", "idle"));
        assertTrue(ladder.outranks("hazard", "idle"));
        assertThrows(IllegalArgumentException.class, () -> ladder.rank("unknown"));
        assertThrows(IllegalArgumentException.class,
            () -> PriorityLadder.of(List.of("hazard", "hazard")));
    }

    /**
     * The historical shape. Four families write the ladder as a switch over their phase enum with a
     * default arm, so a phase added later silently lands on the default rank and quietly outranks or
     * yields to everything by accident. An enum built ladder ranks every constant by construction.
     */
    @Test
    void redAHandWrittenSwitchGivesANewPhaseTheDefaultRankWhileTheLadderCannot() {
        // The defective shape, reproduced: NEWLY_ADDED was never given a case.
        enum LatePhase { HAZARD, DEFEND, WANDER, NEWLY_ADDED }
        final java.util.function.ToIntFunction<LatePhase> handWritten = phase -> switch (phase) {
            case HAZARD -> 0;
            case DEFEND -> 1;
            case WANDER -> 2;
            default -> 0;
        };
        assertEquals(handWritten.applyAsInt(LatePhase.HAZARD),
            handWritten.applyAsInt(LatePhase.NEWLY_ADDED),
            "the forgotten phase silently ranks as urgently as a hazard");

        final PriorityLadder<LatePhase> ladder = PriorityLadder.ofEnum(LatePhase.class);
        assertEquals(3, ladder.rank(LatePhase.NEWLY_ADDED));
        assertTrue(ladder.outranks(LatePhase.HAZARD, LatePhase.NEWLY_ADDED),
            "the ladder ranks it where it was declared, never as a hazard by omission");
    }
}
