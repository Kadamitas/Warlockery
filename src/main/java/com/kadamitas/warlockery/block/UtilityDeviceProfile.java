package com.kadamitas.warlockery.block;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum UtilityDeviceProfile {
    BLOOD_CRUCIBLE("bloodcrucible"),
    COFFIN("coffinblock"),
    DISEASE("disease"),
    GARLIC_WARD("garlicgarland"),
    LEECH_CHEST("leechchest"),
    MIRROR("mirrorblock", "mirrorblock2", "mirrorwall"),
    PIT_SOIL("pitdirt", "pitgrass"),
    SHADED_GLASS("shadedglass", "shadedglass_active"),
    SPIRIT_PORTAL("spiritportal"),
    TRENT_EFFIGY("trent"),
    WOLF_ALTAR("wolfaltar");

    private static final Map<String, UtilityDeviceProfile> BY_ID = Arrays.stream(values())
        .flatMap(profile -> profile.ids.stream().map(id -> Map.entry(id, profile)))
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    private final Set<String> ids;

    UtilityDeviceProfile(final String... ids) {
        this.ids = Set.of(ids);
    }

    public Set<String> ids() {
        return ids;
    }

    public static Optional<UtilityDeviceProfile> find(final String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Set<String> blockIds() {
        return BY_ID.keySet();
    }
}
