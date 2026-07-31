package com.kadamitas.warlockery.menu;

import java.util.List;
import java.util.Map;

public record MachineUiLayout(String kind, List<SlotPosition> slots, int accent, int panel) {
    private static final List<SlotPosition> CRUCIBLE = List.of(
        at(24, 28), at(44, 16), at(64, 28), at(44, 46), at(44, 68),
        at(118, 18), at(138, 30), at(118, 42), at(138, 54)
    );
    private static final List<SlotPosition> WHEEL = List.of(
        at(20, 18), at(42, 12), at(64, 18), at(20, 46), at(42, 52), at(64, 46),
        at(118, 18), at(138, 32), at(118, 46)
    );
    private static final List<SlotPosition> VESSEL = List.of(
        at(18, 20), at(40, 12), at(62, 20), at(18, 48), at(40, 56), at(62, 48),
        at(120, 18), at(140, 34), at(120, 50)
    );
    private static final Map<String, MachineUiLayout> LAYOUTS = Map.ofEntries(
        entry("alchemical_oven", CRUCIBLE, 0xFFCC5B2C, 0xFF3A211D),
        entry("distillery", CRUCIBLE, 0xFF9B67C8, 0xFF241B35),
        entry("kettle", VESSEL, 0xFF6CBF70, 0xFF183329),
        entry("cauldron", VESSEL, 0xFF4D9CA7, 0xFF172F34),
        entry("silvervat", VESSEL, 0xFF9ED6DC, 0xFF26363E),
        entry("spinningwheel", WHEEL, 0xFFD3A45D, 0xFF3A2A1B),
        entry("brazier", VESSEL, 0xFFF07835, 0xFF3C1B18)
    );

    public MachineUiLayout {
        slots = List.copyOf(slots);
        if (slots.size() != 9) {
            throw new IllegalArgumentException("Machine layouts require nine slots");
        }
    }

    public static MachineUiLayout forKind(final String kind) {
        return LAYOUTS.getOrDefault(kind, LAYOUTS.get("cauldron"));
    }

    private static Map.Entry<String, MachineUiLayout> entry(
        final String kind,
        final List<SlotPosition> slots,
        final int accent,
        final int panel
    ) {
        return Map.entry(kind, new MachineUiLayout(kind, slots, accent, panel));
    }

    private static SlotPosition at(final int x, final int y) {
        return new SlotPosition(x, y);
    }

    public record SlotPosition(int x, int y) {
    }
}
