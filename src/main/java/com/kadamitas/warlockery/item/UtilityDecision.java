package com.kadamitas.warlockery.item;

import java.util.Objects;

public record UtilityDecision(boolean success, String diagnostic) {
    public UtilityDecision {
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic").strip();
        if (diagnostic.isEmpty()) {
            throw new IllegalArgumentException("A utility decision requires a diagnostic");
        }
    }

    public static UtilityDecision failure(final String diagnostic) {
        return new UtilityDecision(false, diagnostic);
    }

    public static UtilityDecision success(final String diagnostic) {
        return new UtilityDecision(true, diagnostic);
    }

    public String messageKey(final String family) {
        return "message.warlockery." + family + "." + diagnostic;
    }
}
