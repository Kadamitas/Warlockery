package com.kadamitas.warlockery.block;

import java.util.Optional;
import java.util.Set;

public final class FetishBindingRules {
    private FetishBindingRules() {
    }

    public static Optional<FetishMode> select(final Set<String> spectralKinds) {
        if (spectralKinds.containsAll(Set.of("spectre", "banshee", "poltergeist"))) {
            return Optional.of(FetishMode.VOODOO_PROTECTION);
        }
        if (spectralKinds.contains("banshee")) {
            return Optional.of(FetishMode.SHRIEKING);
        }
        if (spectralKinds.contains("spectre")) {
            return Optional.of(FetishMode.SENTINEL);
        }
        if (spectralKinds.contains("poltergeist")) {
            return Optional.of(FetishMode.DISORIENTATION);
        }
        return spectralKinds.isEmpty() ? Optional.empty() : Optional.of(FetishMode.GHOST_WALKING);
    }
}
