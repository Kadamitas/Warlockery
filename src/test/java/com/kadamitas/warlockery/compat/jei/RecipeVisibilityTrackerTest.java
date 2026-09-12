package com.kadamitas.warlockery.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RecipeVisibilityTrackerTest {
    record Recipe(String id, String definition) { }

    @Test
    void unchangedRefreshKeepsTheOriginalVisibleRecipeWithoutDuplicateRegistration() {
        final var fixture = new Fixture();
        final var original = new Recipe("recipe", "old");
        fixture.reset(List.of(original));
        fixture.update(List.of(new Recipe("recipe", "old")));
        fixture.update(List.of(new Recipe("recipe", "old")));
        assertEquals(List.of(original), fixture.visible());
        assertSame(original, fixture.visible().getFirst());
        assertEquals(1, fixture.recipes.size());
        assertTrue(fixture.hidden.isEmpty());
    }

    @Test
    void replacingADefinitionHidesItsOldRecipeAndRegistersOnlyTheReplacement() {
        final var fixture = new Fixture();
        final var original = new Recipe("recipe", "old");
        final var replacement = new Recipe("recipe", "new");
        fixture.reset(List.of(original));
        fixture.update(List.of(replacement));
        assertEquals(List.of(replacement), fixture.visible());
        assertTrue(fixture.hidden.contains(original));
        assertEquals(2, fixture.recipes.size());
    }

    @Test
    void removedThenEqualReaddedDefinitionRestoresTheFirstRegisteredIdentity() {
        final var fixture = new Fixture();
        final var original = new Recipe("recipe", "old");
        fixture.reset(List.of(original));
        fixture.update(List.of());
        assertTrue(fixture.visible().isEmpty());
        fixture.update(List.of(new Recipe("recipe", "old")));
        assertEquals(1, fixture.visible().size());
        assertSame(original, fixture.visible().getFirst());
        assertEquals(1, fixture.recipes.size());
    }

    @Test
    void revertingAChangedDefinitionRestoresTheHistoricalOriginalAndHidesReplacement() {
        final var fixture = new Fixture();
        final var original = new Recipe("recipe", "old");
        final var replacement = new Recipe("recipe", "new");
        fixture.reset(List.of(original));
        fixture.update(List.of(replacement));
        fixture.update(List.of(new Recipe("recipe", "old")));
        assertSame(original, fixture.visible().getFirst());
        assertEquals(1, fixture.visible().size());
        assertTrue(fixture.hidden.contains(replacement));
        assertEquals(2, fixture.recipes.size());
    }

    @Test
    void catalogInitiallyEmptyThenPopulatedRegistersNewRecipes() {
        final var fixture = new Fixture();
        fixture.reset(List.of());
        final var recipe = new Recipe("recipe", "definition");
        fixture.update(List.of(recipe));
        assertEquals(List.of(recipe), fixture.visible());
        assertEquals(1, fixture.recipes.size());
    }

    @Test
    void newRuntimeDropsHistoricalIdentitiesAndUsesItsOwnInitialRegistration() {
        final var tracker = new RecipeVisibilityTracker<Recipe>();
        final var old = new Recipe("recipe", "definition");
        tracker.reset(List.of(old));
        tracker.update(List.of());
        tracker.clear();
        final var fresh = new Recipe("recipe", "definition");
        final var initial = tracker.update(List.of(fresh));
        assertEquals(List.of(fresh), initial.add());
        assertSame(fresh, initial.add().getFirst());
        assertTrue(initial.show().isEmpty());
        tracker.reset(List.of(fresh));
        assertTrue(tracker.update(List.of(new Recipe("recipe", "definition"))).add().isEmpty());
    }

    @Test
    void anUnchangedRecipeStaysVisibleWhenAnotherIsRemovedOrAdded() {
        final var fixture = new Fixture();
        final var stable = new Recipe("stable", "definition");
        final var removed = new Recipe("removed", "definition");
        final var added = new Recipe("added", "definition");
        fixture.reset(List.of(stable, removed));
        fixture.update(List.of(new Recipe("stable", "definition"), added));
        assertEquals(List.of(stable, added), fixture.visible());
        assertSame(stable, fixture.visible().getFirst());
        assertTrue(fixture.hidden.contains(removed));
        assertEquals(3, fixture.recipes.size());
    }

    // Pinned JEI performs equality distinct before filtering its identity-based hidden set.
    private static final class Fixture {
        final RecipeVisibilityTracker<Recipe> tracker = new RecipeVisibilityTracker<>();
        final List<Recipe> recipes = new ArrayList<>();
        final Set<Recipe> hidden = Collections.newSetFromMap(new IdentityHashMap<>());

        void reset(final List<Recipe> initial) {
            recipes.clear();
            hidden.clear();
            recipes.addAll(initial);
            tracker.reset(initial);
        }

        void update(final List<Recipe> desired) {
            final var changes = tracker.update(desired);
            hidden.addAll(changes.hide());
            hidden.removeAll(changes.show());
            changes.add().stream().filter(recipe -> !hidden.contains(recipe)).forEach(recipes::add);
        }

        List<Recipe> visible() {
            return recipes.stream().distinct().filter(recipe -> !hidden.contains(recipe)).toList();
        }
    }
}
