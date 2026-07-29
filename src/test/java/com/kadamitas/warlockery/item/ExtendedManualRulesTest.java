package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ExtendedManualRulesTest {
    @Test
    void ordinaryUseReadsTheManual() {
        assertEquals(
            ExtendedManualRules.Diagnostic.READ_MANUAL,
            ExtendedManualRules.diagnose(true, false, false)
        );
        assertEquals(
            ExtendedManualRules.Diagnostic.READ_MANUAL,
            ExtendedManualRules.diagnose(false, true, true)
        );
    }

    @Test
    void crouchingWithoutPaperReportsTheMissingInput() {
        assertEquals(
            ExtendedManualRules.Diagnostic.MISSING_PAPER,
            ExtendedManualRules.diagnose(true, true, false)
        );
    }

    @Test
    void crouchingWithPaperCreatesABiomeNote() {
        assertEquals(
            ExtendedManualRules.Diagnostic.CREATE_BIOME_NOTE,
            ExtendedManualRules.diagnose(true, true, true)
        );
    }
}
