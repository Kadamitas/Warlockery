package com.kadamitas.warlockery.mutation;

import java.util.List;
import java.util.stream.Collectors;

public record AdvancedMutationAssessment(
    AdvancedMutationKind kind,
    int satisfied,
    int required,
    List<MissingCondition> missing
) {
    public AdvancedMutationAssessment {
        missing = List.copyOf(missing);
        if (satisfied < 0 || required < 1 || satisfied > required) {
            throw new IllegalArgumentException("Mutation progress must fit its requirement count");
        }
    }

    public boolean complete() {
        return missing.isEmpty();
    }

    public double progress() {
        return (double) satisfied / required;
    }

    public String diagnostic() {
        if (complete()) {
            return "\u2713 " + kind.displayName() + " mutation is ready";
        }
        return kind.displayName() + " mutation missing: " + missing.stream()
            .map(MissingCondition::diagnostic)
            .collect(Collectors.joining("; "));
    }

    public record MissingCondition(String id, String name, int present, int required) {
        public MissingCondition {
            if (id.isBlank() || name.isBlank() || present < 0 || required < 1 || present >= required) {
                throw new IllegalArgumentException("Missing conditions must describe an unmet requirement");
            }
        }

        public String diagnostic() {
            return required == 1 ? name : name + " (" + present + "/" + required + ")";
        }
    }
}
