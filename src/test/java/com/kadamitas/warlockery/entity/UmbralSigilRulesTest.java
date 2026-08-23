package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.UmbralSigilRules.Phase;
import com.kadamitas.warlockery.entity.UmbralSigilRules.SealEnd;
import com.kadamitas.warlockery.entity.UmbralSigilRules.SubjectObservation;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class UmbralSigilRulesTest {

    /**
     * Bootstrapped here rather than relying on a neighbour, so a filtered single-class run behaves
     * exactly like a full-matrix run.
     */
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static SubjectObservation held() {
        return new SubjectObservation(true, true, true, true, true, 0.0D, 0);
    }

    // ---------------------------------------------------------------- geometry

    @Test
    void theSealHasExactlyThreeDistinctVerticesAndNoneOfThemIsTheCentre() {
        final BlockPos centre = new BlockPos(10, 64, -7);
        final Set<BlockPos> vertices = new HashSet<>();
        for (int index = 0; index < UmbralSigilRules.SEAL_VERTICES; index++) {
            final BlockPos vertex = UmbralSigilRules.vertex(centre, index);
            assertNotEquals(centre, vertex, "a vertex is never the centre itself");
            assertTrue(vertices.add(vertex), "vertex " + index + " duplicates an earlier one");
        }
        assertEquals(3, vertices.size());
    }

    @Test
    void everyVertexStaysInsideTheDeclaredRadiusAndTheSealStaysHorizontal() {
        final BlockPos centre = new BlockPos(0, 70, 0);
        for (int index = 0; index < UmbralSigilRules.SEAL_VERTICES; index++) {
            final BlockPos vertex = UmbralSigilRules.vertex(centre, index);
            assertTrue(Math.abs(vertex.getX() - centre.getX()) <= UmbralSigilRules.SEAL_RADIUS);
            assertTrue(Math.abs(vertex.getZ() - centre.getZ()) <= UmbralSigilRules.SEAL_RADIUS);
            assertEquals(centre.getY() + UmbralSigilRules.SEAL_LIFT, vertex.getY(),
                "a seal is horizontal; no vertex may drift off the snapshot plane");
        }
    }

    @Test
    void theVertexTableIsClockwiseAndIndexingIsTotal() {
        final BlockPos centre = BlockPos.ZERO;
        // North, then south-east, then south-west, viewed from above with +x east and +z south.
        assertEquals(new BlockPos(0, 0, -1), UmbralSigilRules.vertex(centre, 0));
        assertEquals(new BlockPos(1, 0, 1), UmbralSigilRules.vertex(centre, 1));
        assertEquals(new BlockPos(-1, 0, 1), UmbralSigilRules.vertex(centre, 2));
        // A corrupted or out-of-range index can never read outside the fixed table.
        assertEquals(UmbralSigilRules.vertex(centre, 0), UmbralSigilRules.vertex(centre, 3));
        assertEquals(UmbralSigilRules.vertex(centre, 2), UmbralSigilRules.vertex(centre, -1));
    }

    /**
     * The far-vertex coverage guard. A seal that could only consult vertex 0 would trace one point
     * forever, which is the family of defect that has broken five other families' searches. Walking
     * the phase machine from the first inscribe must therefore visit every index exactly once and
     * arrive at the close.
     */
    @Test
    void walkingThePhaseMachineVisitsEveryVertexExactlyOnceAndThenCloses() {
        final Set<Integer> visited = new HashSet<>();
        Phase phase = Phase.INSCRIBE_1;
        for (int step = 0; step < UmbralSigilRules.SEAL_VERTICES; step++) {
            final int index = UmbralSigilRules.vertexIndex(phase);
            assertTrue(index >= 0, phase + " must trace a vertex");
            assertTrue(visited.add(index), "vertex " + index + " is traced twice");
            phase = UmbralSigilRules.phaseAfterVertex(phase);
        }
        assertEquals(Set.of(0, 1, 2), visited);
        assertEquals(Phase.CLOSE, phase, "the only exit from the third vertex is the close");
        assertEquals(-1, UmbralSigilRules.vertexIndex(Phase.CLOSE));
        assertEquals(-1, UmbralSigilRules.vertexIndex(Phase.DORMANT));
        assertEquals(-1, UmbralSigilRules.vertexIndex(Phase.STRIKE));
        assertEquals(-1, UmbralSigilRules.vertexIndex(Phase.RECOVER));
    }

    @Test
    void theSnapshotCentreIsBoundedHorizontallyAndClampedVertically() {
        final BlockPos sigil = new BlockPos(0, 64, 0);
        assertEquals(Optional.of(new BlockPos(3, 64, 4)),
            UmbralSigilRules.sealCentre(sigil, new BlockPos(3, 64, 4)));
        // Beyond the declared bound there is no centre at all, so no seal can begin.
        assertEquals(Optional.empty(),
            UmbralSigilRules.sealCentre(sigil, new BlockPos(400, 64, 0)));
        // A subject far above is pulled back to the Sigil's own flight band.
        assertEquals(Optional.of(new BlockPos(1, 64 + UmbralSigilRules.MAX_CENTRE_LIFT, 1)),
            UmbralSigilRules.sealCentre(sigil, new BlockPos(1, 200, 1)));
        assertEquals(Optional.of(new BlockPos(1, 64 - UmbralSigilRules.MAX_CENTRE_LIFT, 1)),
            UmbralSigilRules.sealCentre(sigil, new BlockPos(1, -40, 1)));
        assertEquals(Optional.empty(), UmbralSigilRules.sealCentre(null, sigil));
        assertEquals(Optional.empty(), UmbralSigilRules.sealCentre(sigil, null));
    }

    // ---------------------------------------------------------------- phases

    @Test
    void onlyDormantHasNoDurationAndEverySealingPhaseIsCancellable() {
        assertEquals(0, UmbralSigilRules.phaseTicks(Phase.DORMANT));
        for (final Phase phase : Phase.values()) {
            if (phase == Phase.DORMANT) {
                continue;
            }
            assertTrue(UmbralSigilRules.phaseTicks(phase) > 0, phase + " must have a duration");
        }
        assertFalse(UmbralSigilRules.sealing(Phase.DORMANT));
        assertFalse(UmbralSigilRules.sealing(Phase.RECOVER));
        assertTrue(UmbralSigilRules.sealing(Phase.INSCRIBE_1));
        assertTrue(UmbralSigilRules.sealing(Phase.INSCRIBE_2));
        assertTrue(UmbralSigilRules.sealing(Phase.INSCRIBE_3));
        assertTrue(UmbralSigilRules.sealing(Phase.CLOSE));
        assertTrue(UmbralSigilRules.sealing(Phase.STRIKE));
    }

    @Test
    void aHazardPreemptsExactlyTheOpenSealPhasesAndNothingElse() {
        for (final Phase phase : Phase.values()) {
            assertFalse(UmbralSigilRules.hazardPreempts(phase, false),
                "no hazard, no preemption: " + phase);
            assertEquals(UmbralSigilRules.sealing(phase),
                UmbralSigilRules.hazardPreempts(phase, true), phase.toString());
        }
    }

    // ---------------------------------------------------------------- endings

    @Test
    void aHeldSubjectEndsNothing() {
        assertEquals(SealEnd.NONE, UmbralSigilRules.sealEnd(held()));
    }

    @Test
    void everyDeclaredEndingReasonIsReachableFromSomeObservation() {
        assertEquals(SealEnd.SUBJECT_LOST, UmbralSigilRules.sealEnd(
            new SubjectObservation(false, true, true, true, true, 0.0D, 0)));
        assertEquals(SealEnd.DIMENSION_LOST, UmbralSigilRules.sealEnd(
            new SubjectObservation(true, false, true, true, true, 0.0D, 0)));
        assertEquals(SealEnd.SUBJECT_LOST, UmbralSigilRules.sealEnd(
            new SubjectObservation(true, true, false, true, true, 0.0D, 0)));
        assertEquals(SealEnd.SUBJECT_INELIGIBLE, UmbralSigilRules.sealEnd(
            new SubjectObservation(true, true, true, false, true, 0.0D, 0)));
        assertEquals(SealEnd.GEOMETRY_LOST, UmbralSigilRules.sealEnd(
            new SubjectObservation(true, true, true, true, false, 0.0D, 0)));
        assertEquals(SealEnd.ROUTE_FAILURE, UmbralSigilRules.sealEnd(new SubjectObservation(
            true, true, true, true, true, 0.0D, UmbralSigilRules.MAX_ROUTE_FAILURES)));
        assertEquals(SealEnd.LEFT_CENTRE, UmbralSigilRules.sealEnd(new SubjectObservation(
            true, true, true, true, true, UmbralSigilRules.CENTRE_HOLD_SQUARED + 0.01D, 0)));
        // Every constant of the enum is covered by the cases above plus NONE.
        assertEquals(7, SealEnd.values().length);
    }

    @Test
    void theCounterplayBoundaryIsExactAndInclusive() {
        assertTrue(UmbralSigilRules.centreHeld(UmbralSigilRules.CENTRE_HOLD_SQUARED));
        assertFalse(UmbralSigilRules.centreHeld(UmbralSigilRules.CENTRE_HOLD_SQUARED + 1.0E-9D));
        assertEquals(SealEnd.NONE, UmbralSigilRules.sealEnd(new SubjectObservation(
            true, true, true, true, true, UmbralSigilRules.CENTRE_HOLD_SQUARED, 0)));
    }

    @Test
    void oneFewerThanTheFailureCapKeepsTheSealOpen() {
        assertEquals(SealEnd.NONE, UmbralSigilRules.sealEnd(new SubjectObservation(
            true, true, true, true, true, 0.0D, UmbralSigilRules.MAX_ROUTE_FAILURES - 1)));
        assertFalse(UmbralSigilRules.routeExhausted(UmbralSigilRules.MAX_ROUTE_FAILURES - 1));
        assertTrue(UmbralSigilRules.routeExhausted(UmbralSigilRules.MAX_ROUTE_FAILURES));
    }

    // ---------------------------------------------------------------- strike

    @Test
    void theStrikeGateRequiresEveryOneOfItsClauses() {
        assertTrue(UmbralSigilRules.strikeAllowed(0, 5, 4.0D, true, 1.0D));
        assertFalse(UmbralSigilRules.strikeAllowed(
            UmbralSigilRules.MAX_STRIKES, 5, 4.0D, true, 1.0D), "the latch is absolute");
        assertFalse(UmbralSigilRules.strikeAllowed(0, 0, 4.0D, true, 1.0D), "a closed window");
        assertFalse(UmbralSigilRules.strikeAllowed(
            0, 5, UmbralSigilRules.STRIKE_BAND_SQUARED + 0.01D, true, 1.0D), "out of the band");
        assertFalse(UmbralSigilRules.strikeAllowed(0, 5, 4.0D, false, 1.0D), "unseen");
        assertFalse(UmbralSigilRules.strikeAllowed(
            0, 5, 4.0D, true, UmbralSigilRules.CENTRE_HOLD_SQUARED + 0.01D), "outside the centre");
    }

    @Test
    void theSigilAddsNothingToItsOwnRegistryAttackValue() {
        assertEquals(4.0F, UmbralSigilRules.strikeDamage(4.0F));
        assertEquals(0.0F, UmbralSigilRules.strikeDamage(0.0F));
        assertEquals(0.0F, UmbralSigilRules.strikeDamage(-3.0F),
            "a stripped attribute never becomes a negative heal");
        assertEquals(1, UmbralSigilRules.MAX_STRIKES, "one seal, one attempt, never two");
    }

    // ---------------------------------------------------------------- budgets

    @Test
    void everyDeclaredBudgetIsBoundedAndTheHazardCeilingCannotTruncate() {
        assertEquals(UmbralSigilRules.MAX_PLAYER_CANDIDATES
                + UmbralSigilRules.MAX_LINE_OF_SIGHT_CHECKS,
            UmbralSigilRules.MAX_APPOINTMENT_READS,
            "the appointment ceiling must cover a full candidate sweep plus its sight walks");
        assertEquals(3 * 3 * 3, UmbralSigilRules.MAX_HAZARD_READS,
            "the hazard ceiling equals the neighbourhood volume, so it can never truncate");
        assertTrue(UmbralSigilRules.SELECT_INTERVAL_TICKS >= 20);
        assertTrue(UmbralSigilRules.MAX_PATH_STARTS_PER_LEVEL_TICK >= 1);
        assertTrue(UmbralSigilRules.pathStartAllowed(
            UmbralSigilRules.MAX_PATH_STARTS_PER_LEVEL_TICK - 1));
        assertFalse(UmbralSigilRules.pathStartAllowed(
            UmbralSigilRules.MAX_PATH_STARTS_PER_LEVEL_TICK));
    }

    @Test
    void theSealCooldownIsArmedLongEnoughToBeObservable() {
        assertTrue(UmbralSigilRules.SEAL_COOLDOWN_TICKS >= UmbralSigilRules.RECOVER_TICKS);
        assertTrue(UmbralSigilRules.sealStartAllowed(0, false));
        assertFalse(UmbralSigilRules.sealStartAllowed(1, false), "an armed cooldown blocks a seal");
        assertFalse(UmbralSigilRules.sealStartAllowed(0, true), "one seal at a time, always");
    }

    // ---------------------------------------------------------------- frozen identity

    /**
     * The Sigil leaves {@link SpiritMob}, which is what fed the {@code spirit} flag that
     * {@code CreatureCombat} uses for consecrated weakness. The observable matrix must not move.
     * The coordinator-deferred CreatureCombat edit restores the flag for the dedicated entity; the
     * exact numbers it has to reproduce are pinned here, along with the identity facts that stay
     * false so the fix cannot be smuggled in as a classification change instead.
     */
    @Test
    void theFrozenDamageMatrixAndClassificationAreUnchanged() {
        assertEquals(15.0F, CreatureCombat.adjustedDamage(
            CreatureKind.UMBRAL_SIGIL, 10.0F, false, false, true, true),
            "a consecrated bolt keeps its exact 1.5x bonus against the Sigil");
        assertEquals(10.0F, CreatureCombat.adjustedDamage(
            CreatureKind.UMBRAL_SIGIL, 10.0F, false, false, false, true),
            "ordinary damage is exact");
        assertEquals(10.0F, CreatureCombat.adjustedDamage(
            CreatureKind.UMBRAL_SIGIL, 10.0F, true, false, false, true),
            "silver gains no new Sigil weakness");
        assertEquals(10.0F, CreatureCombat.adjustedDamage(
            CreatureKind.UMBRAL_SIGIL, 10.0F, false, true, false, true),
            "wood gains no new Sigil weakness");
        assertFalse(CreatureKind.UMBRAL_SIGIL.isUndead(),
            "the shipped classification stays exactly as registered: the Sigil is not undead");
        assertFalse(CreatureKind.UMBRAL_SIGIL.isDemonic());
        assertFalse(CreatureKind.UMBRAL_SIGIL.isSupernatural());
        assertFalse(CreatureKind.UMBRAL_SIGIL.isWoodenVulnerable());
        assertFalse(FamiliarBondRules.isClassicFamiliar(CreatureKind.UMBRAL_SIGIL));
    }

    @Test
    void theSigilKeepsItsRegisteredVisualProfileAndSpiritArchetype() {
        final CreatureVisualProfile visual =
            CreatureVisualProfile.forKind(CreatureKind.UMBRAL_SIGIL);
        assertEquals(0.7F, visual.width());
        assertEquals(1.44F, visual.height());
        assertEquals(CreatureVisualProfile.Archetype.SPIRIT, visual.archetype());
    }

    @Test
    void theFlyingSpeedIsDeclaredBecauseTheChassisReadsIt() {
        assertEquals(BansheeRules.FLYING_SPEED, UmbralSigilRules.FLYING_SPEED,
            "the Sigil flies on the same declared baseline as its committed flying neighbours");
        assertTrue(UmbralSigilRules.FLYING_SPEED > 0.0D);
        assertTrue(UmbralSigilRules.ESCAPE_SPEED > UmbralSigilRules.ROUTE_SPEED,
            "withdrawal is faster than an approach and both are ordinary navigations");
    }
}
