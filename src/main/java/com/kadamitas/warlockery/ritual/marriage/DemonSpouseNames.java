package com.kadamitas.warlockery.ritual.marriage;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class DemonSpouseNames {
    public static final List<String> ALL = List.of(
        "Agrat",
        "Eisheth",
        "Abyzou",
        "Gremory",
        "Vepar",
        "Astarte",
        "Lamia",
        "Empusa",
        "Onoskelis",
        "Akhkhazu",
        "Batibat",
        "Hannya",
        "Al Basti",
        "Rusalka",
        "Mahishi"
    );

    private DemonSpouseNames() {
    }

    public static Optional<String> firstAvailable(final Set<String> claimed) {
        return ALL.stream().filter(name -> !claimed.contains(name)).findFirst();
    }
}
