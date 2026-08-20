package com.kadamitas.warlockery.entity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AnimalFamiliarBindingRules {
    public enum Kind { EXISTING, VANILLA_CAT, VANILLA_FROG }
    public enum Outcome { REJECT, BIND_IN_PLACE, REPLACE_WITH_FAMILIAR_CAT, REPLACE_WITH_TOAD }
    public enum TransactionStep { ADD_REPLACEMENT, DISCARD_SOURCE }

    public record Candidate(
        Kind kind,
        double distanceSquared,
        UUID identity,
        UUID tameOwner,
        boolean leashed,
        boolean passenger,
        boolean vehicle
    ) {
    }

    private static final Comparator<Candidate> ORDER = Comparator
        .comparingDouble(Candidate::distanceSquared)
        .thenComparing(Candidate::identity, AnimalFamiliarBindingRules::compareUnsigned);

    private AnimalFamiliarBindingRules() {
    }

    public static Optional<Candidate> select(final List<Candidate> candidates, final UUID caster) {
        return candidates.stream().filter(candidate -> outcome(candidate, caster) != Outcome.REJECT).min(ORDER);
    }

    public static Outcome outcome(final Candidate candidate, final UUID caster) {
        if (candidate.leashed() || candidate.passenger() || candidate.vehicle()) {
            return Outcome.REJECT;
        }
        return switch (candidate.kind()) {
            case EXISTING -> Outcome.BIND_IN_PLACE;
            case VANILLA_FROG -> Outcome.REPLACE_WITH_TOAD;
            case VANILLA_CAT -> candidate.tameOwner() == null || candidate.tameOwner().equals(caster)
                ? Outcome.REPLACE_WITH_FAMILIAR_CAT
                : Outcome.REJECT;
        };
    }

    public static List<TransactionStep> transaction(final boolean replacementAdded) {
        return replacementAdded
            ? List.of(TransactionStep.ADD_REPLACEMENT, TransactionStep.DISCARD_SOURCE)
            : List.of();
    }

    private static int compareUnsigned(final UUID left, final UUID right) {
        final int most = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return most != 0 ? most : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }
}
