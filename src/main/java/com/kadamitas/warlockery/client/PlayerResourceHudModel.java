package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.network.ModNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PlayerResourceHudModel {
    private static final Set<String> FOCUS_ITEMS = Set.of("arcane_focus", "mysticbranch");

    private PlayerResourceHudModel() {
    }

    public static List<Meter> meters(
        final ModNetwork.SupernaturalSnapshot snapshot,
        final boolean focusHeld
    ) {
        final List<Meter> meters = new ArrayList<>(2);
        if (isIdentity(snapshot, "werewolf")) {
            meters.add(new Meter(
                Kind.FEROCITY,
                snapshot.resource(),
                snapshot.maxResource(),
                snapshot.selectedPower(),
                -1,
                snapshot.powerCooldownTicks()
            ));
        }
        if (snapshot.magicActive()) {
            meters.add(new Meter(
                Kind.MANA,
                snapshot.magicResource(),
                snapshot.magicMaxResource(),
                snapshot.magicPath(),
                -1,
                0
            ));
        } else if (focusHeld) {
            meters.add(new Meter(Kind.UNATTUNED, 0, 0, "", -1, 0));
        }
        return List.copyOf(meters);
    }

    public static boolean isFocusItem(final String namespace, final String path) {
        return "warlockery".equals(namespace) && FOCUS_ITEMS.contains(path);
    }

    private static boolean isIdentity(
        final ModNetwork.SupernaturalSnapshot snapshot,
        final String identity
    ) {
        return snapshot.identity().equals(identity) || snapshot.identity().endsWith("." + identity);
    }

    public enum Kind {
        BLOOD,
        FEROCITY,
        MANA,
        UNATTUNED
    }

    public record Meter(
        Kind kind,
        int resource,
        int maximum,
        String detail,
        int charges,
        int cooldownTicks
    ) {
        public Meter {
            maximum = Math.max(0, maximum);
            resource = Math.clamp(resource, 0, maximum);
            detail = detail == null ? "" : detail;
            charges = Math.max(-1, charges);
            cooldownTicks = Math.max(0, cooldownTicks);
        }

        public int filledWidth(final int width) {
            return maximum == 0 ? 0 : Math.round(Math.max(0, width) * (float) resource / maximum);
        }

        public int cooldownSeconds() {
            return Math.ceilDiv(cooldownTicks, 20);
        }
    }
}
