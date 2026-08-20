package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnimalFamiliarBindingRulesTest {
    private static final UUID CASTER = new UUID(1L, 1L);
    private static final UUID FOREIGN = new UUID(2L, 2L);

    @Test
    void nearestEligibleCandidateWinsWithUnsignedUuidAsTheStableTieBreak() {
        final var high = candidate(AnimalFamiliarBindingRules.Kind.EXISTING, 4.0, new UUID(-1L, -1L));
        final var low = candidate(AnimalFamiliarBindingRules.Kind.VANILLA_FROG, 4.0, new UUID(0L, 1L));
        assertEquals(Optional.of(low), AnimalFamiliarBindingRules.select(List.of(high, low), CASTER));
    }

    @Test
    void existingFamiliarsBindInPlaceAndVanillaAnimalsChooseExactReplacementBodies() {
        assertEquals(AnimalFamiliarBindingRules.Outcome.BIND_IN_PLACE,
            AnimalFamiliarBindingRules.outcome(candidate(AnimalFamiliarBindingRules.Kind.EXISTING, 1.0, CASTER), CASTER));
        assertEquals(AnimalFamiliarBindingRules.Outcome.REPLACE_WITH_FAMILIAR_CAT,
            AnimalFamiliarBindingRules.outcome(candidate(AnimalFamiliarBindingRules.Kind.VANILLA_CAT, 1.0, CASTER), CASTER));
        assertEquals(AnimalFamiliarBindingRules.Outcome.REPLACE_WITH_TOAD,
            AnimalFamiliarBindingRules.outcome(candidate(AnimalFamiliarBindingRules.Kind.VANILLA_FROG, 1.0, CASTER), CASTER));
    }

    @Test
    void foreignCatsAndTransportAttachedAnimalsAreRejected() {
        assertEquals(AnimalFamiliarBindingRules.Outcome.REJECT,
            AnimalFamiliarBindingRules.outcome(cat(FOREIGN, false, false, false), CASTER));
        assertEquals(AnimalFamiliarBindingRules.Outcome.REJECT,
            AnimalFamiliarBindingRules.outcome(cat(null, true, false, false), CASTER));
        assertEquals(AnimalFamiliarBindingRules.Outcome.REJECT,
            AnimalFamiliarBindingRules.outcome(cat(null, false, true, false), CASTER));
        assertEquals(AnimalFamiliarBindingRules.Outcome.REJECT,
            AnimalFamiliarBindingRules.outcome(cat(null, false, false, true), CASTER));
    }

    @Test
    void replacementTransactionNeverDiscardsBeforeSuccessfulAddition() {
        assertEquals(List.of(AnimalFamiliarBindingRules.TransactionStep.ADD_REPLACEMENT,
                AnimalFamiliarBindingRules.TransactionStep.DISCARD_SOURCE),
            AnimalFamiliarBindingRules.transaction(true));
        assertTrue(AnimalFamiliarBindingRules.transaction(false).isEmpty());
    }

    private static AnimalFamiliarBindingRules.Candidate candidate(
        final AnimalFamiliarBindingRules.Kind kind, final double distance, final UUID id
    ) {
        return new AnimalFamiliarBindingRules.Candidate(kind, distance, id, null, false, false, false);
    }

    private static AnimalFamiliarBindingRules.Candidate cat(
        final UUID owner, final boolean leashed, final boolean passenger, final boolean vehicle
    ) {
        return new AnimalFamiliarBindingRules.Candidate(
            AnimalFamiliarBindingRules.Kind.VANILLA_CAT, 1.0, new UUID(3L, 3L), owner,
            leashed, passenger, vehicle
        );
    }
}
