package com.kadamitas.warlockery.item;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum DollKind {
    TEMPLATE(new DollDefinition("doll", new DollAbility.None(), 0)),
    EARTH_GUARD(lethal("earth_guard_doll", DollBehaviorFactory.earthGuard())),
    WATER_GUARD(lethal("water_guard_doll", DollBehaviorFactory.waterGuard())),
    HUNGER_GUARD(lethal("hunger_guard_doll", DollBehaviorFactory.hungerGuard())),
    FIRE_GUARD(lethal("fire_guard_doll", DollBehaviorFactory.fireGuard())),
    TOOL_MENDING(new DollDefinition(
        "tool_mending_doll", new DollAbility.Mending(DollAbility.RepairTarget.HELD), 128
    )),
    DEATH_GUARD(lethal("death_guard_doll", DollBehaviorFactory.deathGuard())),
    HEX_GUARD(new DollDefinition("hex_guard_doll", new DollAbility.HexGuard(), 32)),
    HEXING(new DollDefinition("hexing_doll", new DollAbility.ActiveHex(), 64)),
    BLOOD_LINK(new DollDefinition("blood_link_doll", new DollAbility.DamageLink(), 64)),
    DOLL_GUARD(new DollDefinition("doll_guard", new DollAbility.DollGuard(), 32)),
    ARMOR_MENDING(new DollDefinition(
        "armor_mending_doll", new DollAbility.Mending(DollAbility.RepairTarget.WORN), 128
    ));

    private static final Map<String, DollKind> BY_ID = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(DollKind::id, Function.identity()));

    private final DollDefinition definition;

    DollKind(final DollDefinition definition) {
        this.definition = definition;
    }

    public String id() {
        return definition.id();
    }

    public DollDefinition definition() {
        return definition;
    }

    public String descriptionKey() {
        return definition.descriptionKey();
    }

    public static Optional<DollKind> find(final String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    private static DollDefinition lethal(final String id, final LethalDollBehavior behavior) {
        return new DollDefinition(id, new DollAbility.LethalProtection(behavior), 32);
    }
}
