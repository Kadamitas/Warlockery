package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.AmbientActivityProfile.ActivityType;
import java.util.Map;

public final class AmbientActivityFactory {
    private static final Map<ActivityType, AmbientActivity> ACTIVITIES = Map.ofEntries(
        Map.entry(ActivityType.WINTER_HEARTH, AmbientActivityRuntime::makeWinterHearth),
        Map.entry(ActivityType.GROVE_TENDING, AmbientActivityRuntime::tendGrove),
        Map.entry(ActivityType.SHINY_CURIOSITY, AmbientActivityRuntime::inspectShinyBlock),
        Map.entry(ActivityType.NIGHT_PERCH, AmbientActivityRuntime::seekNightPerch),
        Map.entry(ActivityType.POND_REST, AmbientActivityRuntime::seekPondRest),
        Map.entry(ActivityType.HAUNTED_BELL, AmbientActivityRuntime::hauntBell),
        Map.entry(ActivityType.STORM_ROD, AmbientActivityRuntime::chargeStormRod),
        Map.entry(ActivityType.ARCANE_STUDY, AmbientActivityRuntime::studyArcana),
        Map.entry(ActivityType.GRAVE_SCAVENGE, AmbientActivityRuntime::scavengeRottenFlesh),
        Map.entry(ActivityType.SOUL_LANTERN_VIGIL, AmbientActivityRuntime::keepSoulLanternVigil),
        Map.entry(ActivityType.HAY_REST, AmbientActivityRuntime::restAtHay),
        Map.entry(ActivityType.VILLAGE_WATCH, AmbientActivityRuntime::patrolVillageBell),
        Map.entry(ActivityType.FAMILIAR_HOME, AmbientActivityRuntime::restNearHome),
        Map.entry(ActivityType.THORN_GARDEN, AmbientActivityRuntime::visitThornGarden),
        Map.entry(ActivityType.MIRROR_GAZE, AmbientActivityRuntime::gazeAtReflection),
        Map.entry(ActivityType.MOON_GAZE, AmbientActivityRuntime::gazeAtMoon)
    );

    private AmbientActivityFactory() {
    }

    public static AmbientActivity create(final ActivityType type) {
        final AmbientActivity activity = ACTIVITIES.get(type);
        if (activity == null) {
            throw new IllegalArgumentException("No ambient activity implementation for " + type);
        }
        return activity;
    }
}
