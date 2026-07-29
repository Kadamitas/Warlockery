package com.kadamitas.warlockery.mutation;

import com.kadamitas.warlockery.mutation.AdvancedMutationAssessment.MissingCondition;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class AdvancedMutationRules {
    public static final int REQUIRED_SLIME_SNARES = 2;
    public static final int REQUIRED_GRASSPERS = 4;
    public static final int REQUIRED_TOAD_EXTREMIS = 3;
    public static final int REQUIRED_MINEDRAKE_EXTREMIS = 2;
    public static final int REQUIRED_MANDRAKE_CROPS = 4;

    private AdvancedMutationRules() {
    }

    public static AdvancedMutationAssessment assess(
        final AdvancedMutationKind kind,
        final AdvancedMutationSnapshot snapshot
    ) {
        final List<Requirement> requirements = switch (kind) {
            case TOAD -> List.of(
                requirement("a cobweb", snapshot.cobweb() ? 1 : 0, 1),
                requirement("water beneath the cobweb", snapshot.water() ? 1 : 0, 1),
                requirement("slime-filled Critter Snares", snapshot.slimeSnares(), REQUIRED_SLIME_SNARES),
                requirement("diagonal Grasspers", snapshot.diagonalGrasspers(), REQUIRED_GRASSPERS),
                requirement("Mutandis Extremis", snapshot.mutandisExtremis(), REQUIRED_TOAD_EXTREMIS),
                requirement("a charged Attuned Stone", snapshot.chargedAttunedStones(), 1),
                requirement("a cat or ocelot host", snapshot.toadHosts(), 1)
            );
            case MINEDRAKE -> List.of(
                requirement("a cobweb", snapshot.cobweb() ? 1 : 0, 1),
                requirement("water beneath the cobweb", snapshot.water() ? 1 : 0, 1),
                requirement("mature cardinal Mandrakes", snapshot.matureCardinalMandrakes(), REQUIRED_MANDRAKE_CROPS),
                requirement("diagonal Grasspers", snapshot.diagonalGrasspers(), REQUIRED_GRASSPERS),
                requirement("Mutandis Extremis", snapshot.mutandisExtremis(), REQUIRED_MINEDRAKE_EXTREMIS),
                requirement("Focused Will", snapshot.focusedWill(), 1),
                requirement("a charged Attuned Stone", snapshot.chargedAttunedStones(), 1),
                requirement("a Creeper host", snapshot.creeperHosts(), 1),
                requirement("a living Mandrake host", snapshot.livingMandrakes(), 1)
            );
        };
        final int required = requirements.stream().mapToInt(Requirement::required).sum();
        final int satisfied = requirements.stream()
            .mapToInt(requirement -> Math.min(requirement.present(), requirement.required()))
            .sum();
        final List<MissingCondition> missing = requirements.stream()
            .filter(requirement -> requirement.present() < requirement.required())
            .map(requirement -> new MissingCondition(
                requirement.name(),
                requirement.present(),
                requirement.required()
            ))
            .toList();
        return new AdvancedMutationAssessment(kind, satisfied, required, missing);
    }

    public static AdvancedMutationAssessment select(final AdvancedMutationSnapshot snapshot) {
        return Stream.of(AdvancedMutationKind.values())
            .map(kind -> assess(kind, snapshot))
            .max(Comparator
                .comparing(AdvancedMutationAssessment::complete)
                .thenComparingInt(assessment -> intentScore(assessment.kind(), snapshot))
                .thenComparingDouble(AdvancedMutationAssessment::progress))
            .orElseThrow();
    }

    private static int intentScore(
        final AdvancedMutationKind kind,
        final AdvancedMutationSnapshot snapshot
    ) {
        return switch (kind) {
            case TOAD -> snapshot.slimeSnares() * 2 + snapshot.toadHosts() * 4;
            case MINEDRAKE -> snapshot.matureCardinalMandrakes() * 2
                + snapshot.focusedWill() * 2
                + snapshot.creeperHosts() * 4
                + snapshot.livingMandrakes() * 4;
        };
    }

    private static Requirement requirement(final String name, final int present, final int required) {
        return new Requirement(name, Math.max(0, present), required);
    }

    private record Requirement(String name, int present, int required) {
    }
}
