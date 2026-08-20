package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RitualUiStateTest {
    @Test
    void theChecklistLeavesOutTheAltarPowerRow() {
        final List<String> labels = RitualUiState.checklistRows(option(crowdedChecklist())).stream()
            .map(RitualManager.RequirementStatus::label)
            .toList();
        assertTrue(!labels.contains("altar_power"), "altar power has its own panel line and must not repeat as a row");
    }

    @Test
    void unmetRequirementsAreListedBeforeMetOnes() {
        final List<RitualManager.RequirementStatus> rows = RitualUiState.checklistRows(option(crowdedChecklist()));
        final int lastUnmet = lastIndexMatching(rows, row -> !row.met());
        final int firstMet = firstIndexMatching(rows, RitualManager.RequirementStatus::met);
        assertTrue(
            lastUnmet < firstMet,
            "a blocked player must read the unmet rows first, got " + labels(rows)
        );
    }

    @Test
    void everyUnmetRequirementSurvivesTheRowBudget() {
        final List<RitualManager.RequirementStatus> rows = RitualUiState.checklistRows(option(crowdedChecklist()));
        final List<String> hidden = rows.stream()
            .skip(RitualUiState.CHECKLIST_ROWS)
            .filter(row -> !row.met())
            .map(RitualManager.RequirementStatus::label)
            .toList();
        assertTrue(
            hidden.isEmpty(),
            "a rite over the row budget must not collapse the only reasons it is blocked: " + hidden
        );
    }

    @Test
    void orderingWithinTheMetAndUnmetGroupsIsPreserved() {
        final List<RitualManager.RequirementStatus> rows = RitualUiState.checklistRows(option(crowdedChecklist()));
        assertEquals(
            List.of("night", "ritual_inhibitors"),
            rows.stream().filter(row -> !row.met()).map(RitualManager.RequirementStatus::label).toList(),
            "unmet rows must keep the order the site inspection produced"
        );
        assertEquals(
            List.of(
                "spirit_world_circle_magic", "circleglyphritual", "circleglyphinfernal", "circleglyph_veil",
                "warlockery:ingredient_bone_meal", "warlockery:ingredient_grave_dust", "warlockery:soul_shard",
                "warlockery:spirit", "structure", "day", "full_moon", "minecraft:the_nether", "coven",
                "circle_center", "inactive"
            ),
            rows.stream().filter(RitualManager.RequirementStatus::met)
                .map(RitualManager.RequirementStatus::label)
                .toList(),
            "met rows must keep the order the site inspection produced"
        );
    }

    /**
     * The shape a large rite produces: the reasons a player is actually blocked are appended near the end of
     * the inspection, well past the number of rows the panel can show.
     */
    private static List<RitualManager.RequirementStatus> crowdedChecklist() {
        final List<RitualManager.RequirementStatus> requirements = new ArrayList<>();
        requirements.add(condition("spirit_world_circle_magic", true));
        requirements.add(new RitualManager.RequirementStatus("chalk", "circleglyphritual", 16, 16, true));
        requirements.add(new RitualManager.RequirementStatus("chalk", "circleglyphinfernal", 28, 28, true));
        requirements.add(new RitualManager.RequirementStatus("chalk", "circleglyph_veil", 40, 40, true));
        requirements.add(new RitualManager.RequirementStatus("ingredient", "warlockery:ingredient_bone_meal", 1, 1, true));
        requirements.add(new RitualManager.RequirementStatus("ingredient", "warlockery:ingredient_grave_dust", 1, 1, true));
        requirements.add(new RitualManager.RequirementStatus("ingredient", "warlockery:soul_shard", 1, 1, true));
        requirements.add(new RitualManager.RequirementStatus("entity", "warlockery:spirit", 15, 15, true));
        requirements.add(new RitualManager.RequirementStatus("altar", "structure", 1, 1, true));
        requirements.add(new RitualManager.RequirementStatus("power", "altar_power", 6000, 6000, true));
        requirements.add(condition("night", false));
        requirements.add(condition("day", true));
        requirements.add(condition("full_moon", true));
        requirements.add(new RitualManager.RequirementStatus("condition", "minecraft:the_nether", 1, 1, true));
        requirements.add(new RitualManager.RequirementStatus("coven", "coven", 3, 3, true));
        requirements.add(new RitualManager.RequirementStatus("condition", "ritual_inhibitors", 0, 1, false));
        requirements.add(new RitualManager.RequirementStatus("center", "circle_center", 1, 1, true));
        requirements.add(new RitualManager.RequirementStatus("session", "inactive", 1, 1, true));
        return List.copyOf(requirements);
    }

    private static RitualManager.RequirementStatus condition(final String label, final boolean met) {
        return new RitualManager.RequirementStatus("condition", label, 1, met ? 1 : 0, met);
    }

    private static RitualManager.RitualOption option(final List<RitualManager.RequirementStatus> requirements) {
        return new RitualManager.RitualOption(
            "warlockery:crowded_rite",
            "ritual.warlockery.crowded_rite.title",
            "ritual.warlockery.crowded_rite.description",
            6000,
            6000,
            300,
            requirements,
            false
        );
    }

    private static List<String> labels(final List<RitualManager.RequirementStatus> rows) {
        return rows.stream().map(RitualManager.RequirementStatus::label).toList();
    }

    private static int firstIndexMatching(
        final List<RitualManager.RequirementStatus> rows,
        final java.util.function.Predicate<RitualManager.RequirementStatus> match
    ) {
        return java.util.stream.IntStream.range(0, rows.size())
            .filter(index -> match.test(rows.get(index)))
            .findFirst()
            .orElse(rows.size());
    }

    private static int lastIndexMatching(
        final List<RitualManager.RequirementStatus> rows,
        final java.util.function.Predicate<RitualManager.RequirementStatus> match
    ) {
        return java.util.stream.IntStream.range(0, rows.size())
            .filter(index -> match.test(rows.get(index)))
            .max()
            .orElse(-1);
    }
}
