package com.kadamitas.warlockery.magic;

import java.util.EnumMap;
import java.util.Map;

public record MagicPathProfile(
    MagicPath path,
    int selfCost,
    int targetCost,
    int worldCost,
    String passiveDescription
) {
    private static final Map<MagicPath, MagicPathProfile> PROFILES = profiles();

    public MagicPathProfile {
        if (selfCost < 0 || targetCost < 0 || worldCost < 0 || passiveDescription.isBlank()) {
            throw new IllegalArgumentException("Magic path costs and descriptions must be valid");
        }
    }

    public static MagicPathProfile forPath(final MagicPath path) {
        return PROFILES.get(path);
    }

    public int cost(final MagicPathRules.ActionKind action) {
        return switch (action) {
            case SELF -> selfCost;
            case TARGET -> targetCost;
            case WORLD -> worldCost;
        };
    }

    private static Map<MagicPath, MagicPathProfile> profiles() {
        final EnumMap<MagicPath, MagicPathProfile> profiles = new EnumMap<>(MagicPath.class);
        profiles.put(MagicPath.IMP, new MagicPathProfile(MagicPath.IMP, 4, 8, 10, "Bound imp contract magic"));
        profiles.put(MagicPath.INFERNAL, new MagicPathProfile(
            MagicPath.INFERNAL, 12, 10, 6, "Enthrallment and sacrifice power"
        ));
        profiles.put(MagicPath.GRAVE, new MagicPathProfile(
            MagicPath.GRAVE, 4, 10, 5, "Undead command and nourishing kills"
        ));
        profiles.put(MagicPath.LIGHT, new MagicPathProfile(
            MagicPath.LIGHT, 8, 12, 14, "Concealment and hardened light"
        ));
        profiles.put(MagicPath.OTHERWHERE, new MagicPathProfile(
            MagicPath.OTHERWHERE, 10, 14, 8, "Recall and dimensional travel"
        ));
        profiles.put(MagicPath.OVERWORLD, new MagicPathProfile(
            MagicPath.OVERWORLD, 8, 10, 12, "Earth and metal control"
        ));
        profiles.put(MagicPath.SKY, new MagicPathProfile(MagicPath.SKY, 8, 10, 8, "Air movement and safe descent"));
        return Map.copyOf(profiles);
    }
}
