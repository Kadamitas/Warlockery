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
        profile(ActivityType.NIGHT_PERCH, Set.of(CreatureKind.OWL), 300, 8, 3_600, 0),
        profile(ActivityType.POND_REST, Set.of(CreatureKind.TOAD), 300, 8, 3_600, 0),
        profile(ActivityType.HAUNTED_BELL, Set.of(CreatureKind.POLTERGEIST), 400, 14, 6_000, 0),
        profile(ActivityType.STORM_ROD, Set.of(CreatureKind.STORM_SIMIAN), 300, 8, 3_600, 0),
        // F13: the dedicated Hedge Crone and Circle Mage runtimes own their own bounded
        // workstation work, so the generic ARCANE_STUDY dispatch has no remaining kind. The
        // activity type and its block tag set stay registered and are still the shared
        // workstation predicate both dedicated runtimes reuse.
        // F31 delegated LOUSE to ParasyticLouseRuntime, which owns its own bounded feeding and
        // never calls AmbientActivityRuntime. F17 had already removed CORPSE, so LOUSE was the
        // row's last kind and the canonical constructor rejects an empty kind set: the whole row
        // goes, the way F13 retired ARCANE_STUDY and F03 retired DAYLIGHT_SHELTER. Unlike
        // ARCANE_STUDY the ActivityType goes with it, because nothing else reads GRAVE_SCAVENGE --
        // it had no AmbientActivityTags entry and no dedicated runtime reuses it as a predicate.
        // F03 superseded DAYLIGHT_SHELTER outright: VampireCourtRuntime raises SEEK_SHELTER from
        // the same exposed-daylight predicate, then claims one sky-blocked block per member under a
        // level-wide lease that the generic row cannot see, so the generic version could only route
        // a second court member into an already claimed hole. Both declared kinds were court kinds
        // and the canonical constructor rejects an empty kind set, so the whole row is retired the
        // way F13 retired ARCANE_STUDY; the activity type and its dispatch entry go with it because
        // nothing else reads them.
        // F22: UMBRAL_SIGIL was the last kind left on SOUL_LANTERN_VIGIL once F18, F19 and F21
        // delegated DEATH, LOST_SOUL, SPIRIT, ECHO_SHADE and SPECTRE, and the dedicated
        // UmbralSigilRuntime now owns the Sigil's whole schedule. The canonical constructor
        // rejects an empty kind set, so the entire row is retired here rather than emptied,
        // exactly as F13 retired ARCANE_STUDY. The activity type, its SOUL_LIGHTS block tag and
        // its action stay registered: forType returns null and executeNow declines for a retired
        // row, which AmbientActivityRulesTest pins for both retired rows.
        profile(ActivityType.HAY_REST, Set.of(CreatureKind.PALE_STEED, CreatureKind.NIGHTMARE), 400, 12, 6_000, 0),
        // F05 superseded the LYCAN_VILLAGER share of VILLAGE_WATCH. LycanVillagerRuntime raises
        // BOUNDARY_WATCH from its own brain anchor, walks it under the level path budget and faces
        // outward from the anchor, while the generic patrol issues a raw navigation request toward
        // any bell that the villager brain overwrites again on the same tick. The row keeps the two
        // kinds that still reach the generic dispatch.
        profile(ActivityType.VILLAGE_WATCH, Set.of(CreatureKind.IRONBOUND_SENTINEL,
            CreatureKind.WEREWOLF_HUNTER), 300, 10, 3_600, 0),
        profile(ActivityType.FAMILIAR_HOME, Set.of(CreatureKind.CAT, CreatureKind.FAMILIAR), 300, 8, 3_600, 0),
        profile(ActivityType.THORN_GARDEN, Set.of(CreatureKind.THORNED_PURSUER, CreatureKind.MANDRAKE,
            CreatureKind.DREAMROOT, CreatureKind.BRAMBLE_COLOSSUS), 300, 8, 3_600, 0),
        profile(ActivityType.MIRROR_GAZE, Set.of(CreatureKind.GLASS_DOPPELGANGER,
            CreatureKind.ILLUSION_CREEPER, CreatureKind.ILLUSION_SPIDER, CreatureKind.ILLUSION_ZOMBIE),
            300, 10, 3_600, 0),
        // F05 superseded the LYCAN_VILLAGER share of MOON_GAZE with its own MOON_WATCH intent, which
        // gates on a full moon, clear sky, a safe schedule and an anchor before it raises the same
        // head. F04 replaced nothing here: LycanPackRuntime reads the moon only to size a hunt, and
        // an idle sated Werewolf still has no night posture of its own, so WEREWOLF keeps the row
        // and WerewolfEntity reaches it again.
        profile(ActivityType.MOON_GAZE, Set.of(CreatureKind.WEREWOLF), 300, 8, 3_600, 0)
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

        SOUL_LANTERN_VIGIL,
        HAY_REST,
        VILLAGE_WATCH,
        FAMILIAR_HOME,
        THORN_GARDEN,
        MIRROR_GAZE,
        MOON_GAZE
    }
}
