package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.diagnostic.DiagnosticChecklist;
import com.kadamitas.warlockery.diagnostic.ReadinessUiState;
import java.util.List;

public record RitualUiState(DiagnosticChecklist checklist) implements ReadinessUiState {
    /**
     * How many checklist rows the ritual panel has room for before it collapses the rest into a count. Kept
     * well under the requirement cap the ritual payload enforces, so the panel never outgrows what it is sent.
     */
    public static final int CHECKLIST_ROWS = 10;

    /**
     * The requirement rows the ritual checklist shows, in display order. Altar power is left out because the
     * panel already states it on its own line above the checklist.
     *
     * <p>Unmet rows come first. Site inspection appends the conditions a player is most often blocked by last,
     * so in insertion order a large rite pushes its only failing row past {@link #CHECKLIST_ROWS} and the panel
     * reports that requirements are missing without saying which. The partition is stable, so within each group
     * the inspection order a returning player has learned is unchanged.</p>
     */
    public static List<RitualManager.RequirementStatus> checklistRows(final RitualManager.RitualOption option) {
        return option.requirements().stream()
            .filter(requirement -> !"power".equals(requirement.category()))
            .sorted(java.util.Comparator.comparing(RitualManager.RequirementStatus::met))
            .toList();
    }

    /**
     * Whether a cast is already under way at this circle. Site inspection reports it as the session row, and
     * the panel reads that rather than keeping a second copy of the same fact.
     */
    public static boolean castInProgress(final RitualManager.RitualOption option) {
        return option.requirements().stream()
            .filter(requirement -> "session".equals(requirement.category()))
            .anyMatch(requirement -> !requirement.met());
    }

    public static RitualUiState from(final RitualManager.RitualOption option) {
        final DiagnosticChecklist checklist = DiagnosticChecklist.from(
            option.requirements().stream()
                .filter(RitualManager.RequirementStatus::blocksActivation)
                .map(RitualManager.RequirementStatus::met)
        );
        return new RitualUiState(checklist);
    }
}
