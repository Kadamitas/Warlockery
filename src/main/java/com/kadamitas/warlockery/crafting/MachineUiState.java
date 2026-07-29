package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.diagnostic.DiagnosticChecklist;
import com.kadamitas.warlockery.diagnostic.ReadinessUiState;
import java.util.List;

public record MachineUiState(DiagnosticChecklist checklist) implements ReadinessUiState {
    public static MachineUiState from(
        final MachineProfile profile,
        final MachineRecipeManager.Diagnostic diagnostic,
        final MachineStatus machineStatus
    ) {
        return new MachineUiState(DiagnosticChecklist.from(List.of(
            diagnostic.inputsReady(profile),
            machineStatus.canRun()
        )));
    }
}
