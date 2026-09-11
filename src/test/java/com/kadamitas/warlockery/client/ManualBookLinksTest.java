package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.*;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.ManualView;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class ManualBookLinksTest {
    @Test
    void everyReferenceResolvesToAnIndexedChapter() {
        for (var profile : ManualProfile.profiles()) for (var section : profile.sections()) {
            for (var ref : ManualBookLinks.references(profile, section))
                assertEquals(ref, ManualBookLinks.decode(ref.id()).orElseThrow(), profile.id() + "/" + section);
        }
        assertTrue(ManualBookLinks.decode(Identifier.parse("other:manual/ingredient_book_distilling/inputs")).isEmpty());
        assertTrue(ManualBookLinks.decode(Identifier.parse("warlockery:manual/ingredient_book_distilling/unknown")).isEmpty());
    }

    @Test
    void inventoryLookupDoesNotUnlockOrSubstituteBookContents() {
        var profile = ManualProfile.find("ingredient_book_distilling").orElseThrow();
        var target = new ManualBookLinks.Reference(profile, "machine_recipe_distill_vitriol");
        assertTrue(target.ownedView(List.of()).isEmpty());
        assertTrue(target.ownedView(List.of(new ManualView(profile, List.of("preamble")))).isEmpty());
        var complete = new ManualView(profile, profile.sections());
        assertSame(complete, target.ownedView(List.of(complete)).orElseThrow());
        assertEquals(List.of("crafting_ingredient_book_distilling"), target.recipeView().sections());
        assertFalse(target.recipeView().sections().contains(target.section()));
    }
}
