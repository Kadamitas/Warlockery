package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record AmbientActivityProfile(
    ActivityType type,
    Set<CreatureKind> kinds,
    int checkIntervalTicks,
    int chanceDenominator,
    int cooldownTicks,
    int localChangeCap
) {
    private static final List<AmbientActivityProfile> PROFILES = List.of(
        profile(ActivityType.WINTER_HEARTH, Set.of(CreatureKind.DEMON, CreatureKind.HELLHOUND,
            CreatureKind.EMBERHORN_ARCHFIEND, CreatureKind.ABYSSAL_REGENT), 400, 18, 24_000, 1),
        profile(ActivityType.GROVE_TENDING, Set.of(CreatureKind.ENT), 300, 8, 6_000, 1),
        profile(ActivityType.SHINY_CURIOSITY, Set.of(CreatureKind.IMP, CreatureKind.STONEBROKER,
            CreatureKind.FORGEWARDEN), 240, 10, 3_600, 0),
        profile(ActivityType.NIGHT_PERCH, Set.of(CreatureKind.OWL, CreatureKind.HEX_BAT), 300, 8, 3_600, 0),
        profile(ActivityType.POND_REST, Set.of(CreatureKind.TOAD), 300, 8, 3_600, 0),
        profile(ActivityType.HAUNTED_BELL, Set.of(CreatureKind.POLTERGEIST, CreatureKind.BANSHEE), 400, 14, 6_000, 0),
        profile(ActivityType.STORM_ROD, Set.of(CreatureKind.STORM_SIMIAN), 300, 8, 3_600, 0),
        profile(ActivityType.ARCANE_STUDY, Set.of(CreatureKind.CIRCLE_MAGE, CreatureKind.HEDGE_CRONE),
            400, 10, 4_800, 0),
        profile(ActivityType.GRAVE_SCAVENGE, Set.of(CreatureKind.LOUSE), 300, 12, 4_800, 1),
        profile(ActivityType.DAYLIGHT_SHELTER, Set.of(CreatureKind.VAMPIRE, CreatureKind.BLOOD_THRALL), 100, 3, 1_200, 0),
        profile(ActivityType.SOUL_LANTERN_VIGIL, Set.of(CreatureKind.LOST_SOUL, CreatureKind.SPIRIT,
            CreatureKind.SPECTRE, CreatureKind.ECHO_SHADE, CreatureKind.UMBRAL_SIGIL, CreatureKind.DEATH),
            400, 10, 4_800, 0),
        profile(ActivityType.HAY_REST, Set.of(CreatureKind.PALE_STEED, CreatureKind.NIGHTMARE), 400, 12, 6_000, 0),
        profile(ActivityType.VILLAGE_WATCH, Set.of(CreatureKind.IRONBOUND_SENTINEL,
            CreatureKind.WEREWOLF_HUNTER, CreatureKind.LYCAN_VILLAGER), 300, 10, 3_600, 0),
        profile(ActivityType.FAMILIAR_HOME, Set.of(CreatureKind.CAT, CreatureKind.FAMILIAR), 300, 8, 3_600, 0),
        profile(ActivityType.THORN_GARDEN, Set.of(CreatureKind.THORNED_PURSUER, CreatureKind.MANDRAKE,
            CreatureKind.DREAMROOT, CreatureKind.BRAMBLE_COLOSSUS), 300, 8, 3_600, 0),
        profile(ActivityType.MIRROR_GAZE, Set.of(CreatureKind.GLASS_DOPPELGANGER,
            CreatureKind.ILLUSION_CREEPER, CreatureKind.ILLUSION_SPIDER, CreatureKind.ILLUSION_ZOMBIE),
            300, 10, 3_600, 0),
        profile(ActivityType.MOON_GAZE, Set.of(CreatureKind.WEREWOLF, CreatureKind.LYCAN_VILLAGER),
            300, 8, 3_600, 0)
    );
    private static final Map<CreatureKind, List<AmbientActivityProfile>> BY_KIND = PROFILES.stream()
        .flatMap(profile -> profile.kinds().stream().map(kind -> Map.entry(kind, profile)))
        .collect(Collectors.collectingAndThen(
            Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.collectingAndThen(
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList()),
                    List::copyOf
                )
            ),
            Map::copyOf
        ));
    private static final Map<ActivityType, AmbientActivityProfile> BY_TYPE = PROFILES.stream()
        .collect(Collectors.toUnmodifiableMap(AmbientActivityProfile::type, Function.identity()));

    public AmbientActivityProfile {
        kinds = Set.copyOf(kinds);
        if (type == null || kinds.isEmpty() || checkIntervalTicks < 20 || chanceDenominator < 1
            || cooldownTicks < checkIntervalTicks || localChangeCap < 0 || localChangeCap > 4) {
            throw new IllegalArgumentException("Ambient activity profiles require safe bounded scheduling values");
        }
    }

    public static List<AmbientActivityProfile> forKind(final CreatureKind kind) {
        return BY_KIND.getOrDefault(kind, List.of());
    }

    public static AmbientActivityProfile forType(final ActivityType type) {
        return BY_TYPE.get(type);
    }

    public static List<AmbientActivityProfile> all() {
        return PROFILES;
    }

    private static AmbientActivityProfile profile(
        final ActivityType type,
        final Set<CreatureKind> kinds,
        final int interval,
        final int chance,
        final int cooldown,
        final int changeCap
    ) {
        return new AmbientActivityProfile(type, kinds, interval, chance, cooldown, changeCap);
    }

    public enum ActivityType {
        WINTER_HEARTH,
        GROVE_TENDING,
        SHINY_CURIOSITY,
        NIGHT_PERCH,
        POND_REST,
        HAUNTED_BELL,
        STORM_ROD,
        ARCANE_STUDY,
        GRAVE_SCAVENGE,
        DAYLIGHT_SHELTER,
        SOUL_LANTERN_VIGIL,
        HAY_REST,
        VILLAGE_WATCH,
        FAMILIAR_HOME,
        THORN_GARDEN,
        MIRROR_GAZE,
        MOON_GAZE
    }
}
