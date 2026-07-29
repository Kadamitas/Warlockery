package com.kadamitas.warlockery.diagnostic;

import java.util.Collection;
import java.util.stream.Stream;

public record DiagnosticChecklist(int satisfied, int total) {
    public static final String COMPLETE_MARKER = "✓";
    public static final String INCOMPLETE_MARKER = "✗";
    public static final int COMPLETE_COLOR = 0x55FF55;
    public static final int INCOMPLETE_COLOR = 0xFF5555;

    public DiagnosticChecklist {
        if (total < 0 || satisfied < 0 || satisfied > total) {
            throw new IllegalArgumentException("Diagnostic counts must satisfy 0 <= satisfied <= total");
        }
    }

    public static DiagnosticChecklist from(final Collection<Boolean> checks) {
        return from(checks.stream());
    }

    public static DiagnosticChecklist from(final Stream<Boolean> checks) {
        final int[] counts = checks.collect(
            () -> new int[2],
            (result, met) -> {
                result[1]++;
                if (Boolean.TRUE.equals(met)) {
                    result[0]++;
                }
            },
            (left, right) -> {
                left[0] += right[0];
                left[1] += right[1];
            }
        );
        return new DiagnosticChecklist(counts[0], counts[1]);
    }

    public boolean complete() {
        return total > 0 && satisfied == total;
    }

    public String marker() {
        return complete() ? COMPLETE_MARKER : INCOMPLETE_MARKER;
    }

    public int color() {
        return complete() ? COMPLETE_COLOR : INCOMPLETE_COLOR;
    }
}
