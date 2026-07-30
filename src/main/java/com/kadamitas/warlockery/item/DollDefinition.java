package com.kadamitas.warlockery.item;

import java.util.Objects;

public record DollDefinition(
    String id,
    DollAbility ability,
    int durability
) {
    public DollDefinition {
        id = Objects.requireNonNull(id, "id").strip();
        ability = Objects.requireNonNull(ability, "ability");
        if (id.isBlank() || durability < 0) {
            throw new IllegalArgumentException("Doll id and durability must be valid");
        }
    }

    public String descriptionKey() {
        return "tooltip.warlockery.doll." + id;
    }
}
