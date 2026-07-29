package com.kadamitas.warlockery.item;

import java.util.Objects;

public record DollDefinition(
    String id,
    DollAbility ability,
    int durability,
    boolean consumedOnActivation
) {
    public DollDefinition {
        id = Objects.requireNonNull(id, "id").strip();
        ability = Objects.requireNonNull(ability, "ability");
        if (id.isBlank() || durability < 0) {
            throw new IllegalArgumentException("Doll id and durability must be valid");
        }
        if (consumedOnActivation && durability != 0) {
            throw new IllegalArgumentException("A single-use doll cannot also have durability");
        }
    }

    public String descriptionKey() {
        return "tooltip.warlockery.doll." + id;
    }
}
