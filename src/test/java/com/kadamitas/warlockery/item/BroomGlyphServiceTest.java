package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class BroomGlyphServiceTest {
    @Test
    void ordinaryBroomTargetsOnlyTheClickedGlyph() {
        assertEquals(1, BroomGlyphService.maxCandidates(0));
    }

    @Test
    void enchantedBroomUsesABoundedFiveByFiveArea() {
        assertEquals(25, BroomGlyphService.maxCandidates(2));
    }

    @Test
    void invalidGlyphRadiusFailsBeforeWorldMutation() {
        assertThrows(IllegalArgumentException.class, () -> BroomGlyphService.maxCandidates(-1));
        assertThrows(IllegalArgumentException.class, () -> BroomGlyphService.maxCandidates(3));
    }
}
