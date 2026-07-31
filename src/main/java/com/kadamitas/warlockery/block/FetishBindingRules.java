package com.kadamitas.warlockery.block;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FetishBindingRules {
    private FetishBindingRules() {
    }

    public static Optional<FetishMode> select(final Set<String> spectralKinds) {
        return select(spectralKinds.stream().collect(java.util.stream.Collectors.toMap(kind -> kind, _ -> 1)));
    }

    public static Optional<FetishMode> select(final Map<String, Integer> spectralCounts) {
        return plan(spectralCounts).map(BindingPlan::mode);
    }

    public static Optional<BindingPlan> plan(final Map<String, Integer> spectralCounts) {
        return plans().stream().filter(plan -> plan.ready(spectralCounts)).findFirst();
    }

    public static java.util.List<BindingPlan> plans() {
        return java.util.List.of(
            new BindingPlan(FetishMode.VOODOO_PROTECTION, Map.of(
                "spirit", 3, "spectre", 1, "banshee", 1, "poltergeist", 1
            )),
            new BindingPlan(FetishMode.SENTINEL, Map.of("spirit", 3, "spectre", 3)),
            new BindingPlan(FetishMode.SHRIEKING, Map.of("spirit", 3, "banshee", 2)),
            new BindingPlan(FetishMode.DISORIENTATION, Map.of("spirit", 3, "poltergeist", 2)),
            new BindingPlan(FetishMode.GHOST_WALKING, Map.of("spirit", 3, "spectre", 1, "banshee", 1))
        );
    }

    public record BindingPlan(FetishMode mode, Map<String, Integer> requirements) {
        public BindingPlan {
            requirements = Map.copyOf(requirements);
        }

        public boolean ready(final Map<String, Integer> available) {
            return requirements.entrySet().stream().allMatch(entry ->
                available.getOrDefault(entry.getKey(), 0) >= entry.getValue()
            );
        }
    }
}
