package com.kadamitas.warlockery.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class SymbolMagicParityTest {
    @Test
    void mysticBranchExposesEveryOriginalWarlockerySymbol() {
        assertEquals(23, SymbolSpell.VALUES.size());
        assertEquals(23, SymbolSpell.VALUES.stream().map(SymbolSpell::id).collect(Collectors.toSet()).size());
        assertEquals(Set.of(
            "ingredient_brew_soul_anguish",
            "ingredient_brew_soul_hunger",
            "ingredient_brew_soul_fear",
            "ingredient_brew_soul_torment"
        ), SymbolSpell.VALUES.stream().flatMap(spell -> spell.soulIngredient().stream()).collect(Collectors.toSet()));
        assertTrue(SymbolSpell.AGONY.infernal());
        assertTrue(SymbolSpell.DOMINATE.infernal());
        assertTrue(SymbolSpell.ABYSSAL_BANISHMENT.infernal());
    }

    @Test
    void unknownSavedSelectionFallsBackToWitchlight() {
        assertEquals(SymbolSpell.WITCHLIGHT, SymbolBranchState.selected(new CompoundTag()));
    }
}
