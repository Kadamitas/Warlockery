package com.kadamitas.warlockery.item;

import java.util.Objects;

public sealed interface DollAbility {
    record None() implements DollAbility {
    }

    record LethalProtection(LethalDollBehavior behavior) implements DollAbility {
        public LethalProtection {
            Objects.requireNonNull(behavior, "behavior");
        }
    }

    record Mending(RepairTarget target) implements DollAbility {
        public Mending {
            Objects.requireNonNull(target, "target");
        }
    }

    record ActiveHex() implements DollAbility {
    }

    record HexGuard() implements DollAbility {
    }

    record DamageLink() implements DollAbility {
    }

    record DollGuard() implements DollAbility {
    }

    enum RepairTarget {
        HELD,
        WORN
    }
}
