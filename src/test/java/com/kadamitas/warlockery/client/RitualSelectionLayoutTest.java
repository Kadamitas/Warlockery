package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class RitualSelectionLayoutTest {
    @Test
    void acceptedOfferingsAreNotReportedMissingDuringAnActiveCast() {
        var option = new com.kadamitas.warlockery.ritual.RitualManager.RitualOption(
            "warlockery:cook_food", "ritual.warlockery.cook_food.title", "ritual.warlockery.cook_food.description",
            100, 0, 100, java.util.List.of(
                new com.kadamitas.warlockery.ritual.RitualManager.RequirementStatus("session", "inactive", 1, 0, false),
                new com.kadamitas.warlockery.ritual.RitualManager.RequirementStatus("ingredient", "#minecraft:coals", 1, 0, false)
            ), false);
        var rows = RitualSelectionScreen.detailContents(option).stream().map(net.minecraft.network.chat.Component::getString).toList();
        assertTrue(rows.stream().anyMatch(row -> row.contains("screen.warlockery.ritual.casting")));
        assertFalse(rows.stream().anyMatch(row -> row.contains("coals") || row.contains("screen.warlockery.ritual.power")));
    }

    @Test
    void everyChecklistLineCanBeReadWithoutOverlappingActionsAtSupportedScales() {
        var lines = IntStream.range(0, 140).boxed().toList();
        for (int[] size : new int[][] {{320,200}, {427,267}, {640,400}, {1280,800}}) {
            var layout = RitualSelectionLayout.calculate(size[0],size[1]);
            var pages = ManualPagination.pages(lines,layout.detailCapacity(),layout.detailCapacity(),line -> false);
            assertEquals(lines, pages.stream().flatMap(java.util.List::stream).toList());
            assertTrue(layout.detailTop() + layout.detailCapacity() * 11 <= layout.detailBottom());
            assertTrue(layout.detailBottom() + 24 <= layout.actionY());
            assertTrue(layout.actionY() + 20 <= size[1]);
            assertTrue(layout.detailX() + layout.detailWidth() <= size[0]);
            assertTrue(layout.top() + 28 + (layout.rows()-1)*25 + 20 < layout.actionY());
        }
    }
}
