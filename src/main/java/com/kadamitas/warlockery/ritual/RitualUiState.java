package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.diagnostic.DiagnosticChecklist;
import com.kadamitas.warlockery.diagnostic.ReadinessUiState;

public record RitualUiState(DiagnosticChecklist checklist) implements ReadinessUiState {
    public static RitualUiState from(final RitualManager.RitualOption option) {
        final DiagnosticChecklist checklist = DiagnosticChecklist.from(
            option.requirements().stream().map(RitualManager.RequirementStatus::met)
        );
        return new RitualUiState(checklist);
    }
}
