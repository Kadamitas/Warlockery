package com.kadamitas.warlockery.mutation;

import com.kadamitas.warlockery.mutation.AdvancedMutationAssessment.MissingCondition;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class AdvancedMutationRules {
    public static final int REQUIRED_SLIME_SNARES = 4;
    public static final int REQUIRED_BAT_SNARES = 4;
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
                requirement("cobweb", "a cobweb", snapshot.cobweb() ? 1 : 0, 1),
                requirement("water", "water beneath the cobweb", snapshot.water() ? 1 : 0, 1),
                requirement("slime_snares", "slime-filled Critter Snares", snapshot.slimeSnares(), REQUIRED_SLIME_SNARES),
                requirement("grasspers", "diagonal Grasspers", snapshot.diagonalGrasspers(), REQUIRED_GRASSPERS),
                requirement("mutandis_extremis", "Mutandis Extremis", snapshot.mutandisExtremis(), REQUIRED_TOAD_EXTREMIS),
                requirement("attuned_stone", "a charged Attuned Stone", snapshot.chargedAttunedStones(), 1),
                requirement("feline_host", "a cat or ocelot host", snapshot.toadHosts(), 1)
            );
            case OWL -> List.of(
                requirement("cobweb", "a cobweb", snapshot.cobweb() ? 1 : 0, 1),
                requirement("water", "water beneath the cobweb", snapshot.water() ? 1 : 0, 1),
                requirement("bat_snares", "bat-filled Critter Snares", snapshot.batSnares(), REQUIRED_BAT_SNARES),
                requirement("grasspers", "diagonal Grasspers", snapshot.diagonalGrasspers(), REQUIRED_GRASSPERS),
                requirement("mutandis_extremis", "Mutandis Extremis", snapshot.mutandisExtremis(), REQUIRED_TOAD_EXTREMIS),
                requirement("attuned_stone", "a charged Attuned Stone", snapshot.chargedAttunedStones(), 1),
                requirement("canine_host", "a wolf host", snapshot.wolfHosts(), 1)
            );
            case MINEDRAKE -> List.of(
                requirement("cobweb", "a cobweb", snapshot.cobweb() ? 1 : 0, 1),
                requirement("water", "water beneath the cobweb", snapshot.water() ? 1 : 0, 1),
                requirement("mandrake_crops", "mature cardinal Mandrakes", snapshot.matureCardinalMandrakes(), REQUIRED_MANDRAKE_CROPS),
                requirement("grasspers", "diagonal Grasspers", snapshot.diagonalGrasspers(), REQUIRED_GRASSPERS),
                requirement("mutandis_extremis", "Mutandis Extremis", snapshot.mutandisExtremis(), REQUIRED_MINEDRAKE_EXTREMIS),
                requirement("focused_will", "Focused Will", snapshot.focusedWill(), 1),
                requirement("attuned_stone", "a charged Attuned Stone", snapshot.chargedAttunedStones(), 1),
                requirement("creeper_host", "a Creeper host", snapshot.creeperHosts(), 1),
                requirement("mandrake_host", "a living Mandrake host", snapshot.livingMandrakes(), 1)
            );
        };
        final int required = requirements.stream().mapToInt(Requirement::required).sum();
        final int satisfied = requirements.stream()
            .mapToInt(requirement -> Math.min(requirement.present(), requirement.required()))
            .sum();
        final List<MissingCondition> missing = requirements.stream()
            .filter(requirement -> requirement.present() < requirement.required())
            .map(requirement -> new MissingCondition(
                requirement.id(),
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
            case OWL -> snapshot.batSnares() * 2 + snapshot.wolfHosts() * 4;
            case MINEDRAKE -> snapshot.matureCardinalMandrakes() * 2
                + snapshot.focusedWill() * 2
                + snapshot.creeperHosts() * 4
                + snapshot.livingMandrakes() * 4;
        };
    }

    private static Requirement requirement(final String id, final String name, final int present, final int required) {
        return new Requirement(id, name, Math.max(0, present), required);
    }

    private record Requirement(String id, String name, int present, int required) {
    }
}
