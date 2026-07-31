package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public enum SupernaturalPower implements StringIdentified {
    TRANSFIX("transfix", SupernaturalProgression.Path.VAMPIRE, 2, 50, 100),
    BLOOD_RUSH("blood_rush", SupernaturalProgression.Path.VAMPIRE, 4, 10, 20),
    SMASH_STONE("smash_stone", SupernaturalProgression.Path.VAMPIRE, 6, 0, 10),
    BAT_SWARM("bat_swarm", SupernaturalProgression.Path.VAMPIRE, 7, 50, 80),
    MESMERIZE("mesmerize", SupernaturalProgression.Path.VAMPIRE, 8, 50, 100),
    CREATE_VAMPIRE("create_vampire", SupernaturalProgression.Path.VAMPIRE, 9, 125, 200),
    CALL_STORM("call_storm", SupernaturalProgression.Path.VAMPIRE, 10, 50, 1_200),
    TELEPORT("teleport", SupernaturalProgression.Path.VAMPIRE, 10, 50, 100),
    SUMMON_BATS("summon_bats", SupernaturalProgression.Path.VAMPIRE, 10, 50, 300),
    WOLF_FORM("wolf_form", SupernaturalProgression.Path.WEREWOLF, 2, 0, 20),
    FEAST("feast", SupernaturalProgression.Path.WEREWOLF, 4, 20, 80),
    WOLFMAN_FORM("wolfman_form", SupernaturalProgression.Path.WEREWOLF, 5, 0, 20),
    STUN_HOWL("stun_howl", SupernaturalProgression.Path.WEREWOLF, 7, 30, 160),
    CALL_PACK("call_pack", SupernaturalProgression.Path.WEREWOLF, 8, 50, 400);

    private static final EnumLookup<SupernaturalPower> LOOKUP = EnumLookup.create("supernatural power", values());
    private static final Map<SupernaturalProgression.Path, List<SupernaturalPower>> BY_PATH =
        Map.copyOf(Arrays.stream(values()).collect(Collectors.groupingBy(
            SupernaturalPower::path,
            () -> new EnumMap<>(SupernaturalProgression.Path.class),
            Collectors.toUnmodifiableList()
        )));

    private final String id;
    private final SupernaturalProgression.Path path;
    private final int level;
    private final int cost;
    private final int cooldown;

    SupernaturalPower(
        final String id,
        final SupernaturalProgression.Path path,
        final int level,
        final int cost,
        final int cooldown
    ) {
        this.id = id;
        this.path = path;
        this.level = level;
        this.cost = cost;
        this.cooldown = cooldown;
    }

    @Override
    public String id() {
        return id;
    }

    public SupernaturalProgression.Path path() {
        return path;
    }

    public int level() {
        return level;
    }

    public int cost() {
        return cost;
    }

    public int cooldown() {
        return cooldown;
    }

    public String translationKey() {
        return "power.warlockery." + id;
    }

    public static Optional<SupernaturalPower> find(final String id) {
        return Optional.ofNullable(id).flatMap(LOOKUP::find);
    }

    public static List<SupernaturalPower> unlocked(
        final SupernaturalProgression.Path path,
        final int level
    ) {
        return Optional.ofNullable(path).map(BY_PATH::get).orElse(List.of()).stream()
            .filter(power -> power.level <= level)
            .toList();
    }
}
