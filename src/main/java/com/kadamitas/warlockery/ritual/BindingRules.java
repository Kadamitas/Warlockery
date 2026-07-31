package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Optional;

public final class BindingRules {
    private BindingRules() {
    }

    public static Decision decide(
        final Variant variant,
        final boolean focusPresent,
        final boolean targetPresent,
        final boolean alreadyBoundElsewhere
    ) {
        if (!focusPresent) {
            return new Decision(false, Diagnostic.MISSING_FOCUS);
        }
        if (!targetPresent) {
            return new Decision(false, Diagnostic.MISSING_TARGET);
        }
        if (alreadyBoundElsewhere) {
            return new Decision(false, Diagnostic.BOUND_ELSEWHERE);
        }
        return new Decision(true, Diagnostic.READY);
    }

    public enum Variant implements StringIdentified {
        FAMILIAR("familiar"),
        SPECTRAL("spectral"),
        STATUE("statue"),
        BLOODED_WAYSTONE("blooded_waystone");

        private static final EnumLookup<Variant> LOOKUP = EnumLookup.create("binding variant", values());
        private final String id;

        Variant(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<Variant> find(final String id) {
            return LOOKUP.find(id);
        }
    }

    public enum Diagnostic {
        MISSING_FOCUS("missing_binding_focus"),
        MISSING_TARGET("missing_binding_target"),
        BOUND_ELSEWHERE("bound_elsewhere"),
        READY("binding_ready");

        private final String id;

        Diagnostic(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Decision(boolean ready, Diagnostic diagnostic) {
        public String messageKey() {
            return "message.warlockery.binding." + diagnostic.id();
        }
    }
}
