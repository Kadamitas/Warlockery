package com.kadamitas.warlockery.diagnostic;

public interface ReadinessUiState {
    DiagnosticChecklist checklist();

    default boolean showGreenCheck() {
        return checklist().complete();
    }
}
