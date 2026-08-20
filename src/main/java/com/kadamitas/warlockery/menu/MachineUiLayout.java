package com.kadamitas.warlockery.menu;

import java.util.List;
import java.util.Map;

public record MachineUiLayout(
    String kind,
    int width,
    int height,
    int inventoryX,
    int inventoryY,
    int statusY,
    List<SlotPosition> slots,
    int accent,
    int panel
) {
    private static final SlotPosition HIDDEN = new SlotPosition(-1_000, -1_000, SlotRole.UNUSED, false);

    private static final Map<String, MachineUiLayout> LAYOUTS = Map.ofEntries(
        entry(
            "alchemical_oven", 196, 220, 17, 140, 108, 0xFFB84B25, 0xFFD8C2B2,
            at(16, 32, SlotRole.MATERIAL), at(40, 32, SlotRole.MATERIAL),
            at(16, 58, SlotRole.MATERIAL), at(40, 58, SlotRole.JAR),
            at(84, 83, SlotRole.FUEL),
            at(146, 32, SlotRole.PRIMARY_OUTPUT), at(170, 32, SlotRole.FUME_OUTPUT),
            at(146, 58, SlotRole.OUTPUT), at(170, 58, SlotRole.OUTPUT)
        ),
        entry(
            "distillery", 210, 224, 24, 144, 112, 0xFF8051AC, 0xFFD2C7DC,
            at(16, 34, SlotRole.MATERIAL), at(16, 62, SlotRole.MATERIAL),
            at(16, 90, SlotRole.JAR),
            at(164, 34, SlotRole.OUTPUT), at(188, 34, SlotRole.OUTPUT),
            at(164, 62, SlotRole.OUTPUT), at(188, 62, SlotRole.OUTPUT),
            HIDDEN, HIDDEN
        ),
        entry(
            "kettle", 210, 224, 24, 144, 112, 0xFF398A55, 0xFFC7D8CB,
            at(16, 34, SlotRole.INGREDIENT), at(42, 28, SlotRole.INGREDIENT),
            at(68, 34, SlotRole.INGREDIENT), at(16, 78, SlotRole.INGREDIENT),
            at(42, 89, SlotRole.INGREDIENT), at(68, 78, SlotRole.INGREDIENT),
            at(178, 31, SlotRole.OUTPUT), at(178, 57, SlotRole.OUTPUT),
            at(178, 83, SlotRole.OUTPUT)
        ),
        entry(
            "cauldron", 224, 232, 31, 152, 120, 0xFF347F8A, 0xFFC5D8D9,
            at(15, 31, SlotRole.INGREDIENT), at(41, 31, SlotRole.INGREDIENT),
            at(15, 57, SlotRole.INGREDIENT), at(41, 57, SlotRole.INGREDIENT),
            at(15, 83, SlotRole.INGREDIENT), at(41, 83, SlotRole.INGREDIENT),
            at(192, 32, SlotRole.OUTPUT), at(192, 59, SlotRole.OUTPUT),
            at(192, 86, SlotRole.OUTPUT)
        ),
        entry(
            "silvervat", 210, 224, 24, 144, 112, 0xFF568A96, 0xFFD7E0E0,
            at(15, 32, SlotRole.REFINING_INPUT), at(41, 32, SlotRole.REFINING_INPUT),
            at(67, 32, SlotRole.REFINING_INPUT), at(15, 62, SlotRole.REFINING_INPUT),
            at(41, 62, SlotRole.REFINING_INPUT), at(67, 62, SlotRole.REFINING_INPUT),
            at(178, 31, SlotRole.OUTPUT), at(178, 58, SlotRole.OUTPUT),
            at(178, 85, SlotRole.OUTPUT)
        ),
        entry(
            "spinningwheel", 214, 224, 26, 144, 112, 0xFFAA762D, 0xFFDED1B8,
            at(16, 57, SlotRole.FIBRE),
            at(52, 27, SlotRole.MODIFIER), at(52, 57, SlotRole.MODIFIER),
            at(52, 87, SlotRole.MODIFIER),
            at(184, 57, SlotRole.PRIMARY_OUTPUT),
            HIDDEN, HIDDEN, HIDDEN, HIDDEN
        ),
        entry(
            "brazier", 202, 220, 20, 140, 108, 0xFFD05A28, 0xFFDEC3B2,
            at(18, 76, SlotRole.OFFERING), at(48, 34, SlotRole.OFFERING),
            at(78, 76, SlotRole.OFFERING), at(170, 76, SlotRole.ASH_OUTPUT),
            HIDDEN, HIDDEN, HIDDEN, HIDDEN, HIDDEN
        )
    );

    public MachineUiLayout {
        slots = List.copyOf(slots);
        if (kind.isBlank() || width < 176 || height < 185 || inventoryX < 0 || inventoryY < 0
            || statusY < 0 || slots.size() != 9) {
            throw new IllegalArgumentException("Invalid machine UI layout");
        }
    }

    public static MachineUiLayout forKind(final String kind) {
        return LAYOUTS.getOrDefault(kind, LAYOUTS.get("cauldron"));
    }

    private static Map.Entry<String, MachineUiLayout> entry(
        final String kind,
        final int width,
        final int height,
        final int inventoryX,
        final int inventoryY,
        final int statusY,
        final int accent,
        final int panel,
        final SlotPosition... slots
    ) {
        return Map.entry(kind, new MachineUiLayout(
            kind, width, height, inventoryX, inventoryY, statusY, List.of(slots), accent, panel
        ));
    }

    private static SlotPosition at(final int x, final int y, final SlotRole role) {
        return new SlotPosition(x, y, role, true);
    }

    public enum SlotRole {
        MATERIAL,
        INGREDIENT,
        REFINING_INPUT,
        FIBRE,
        MODIFIER,
        OFFERING,
        JAR,
        FUEL,
        PRIMARY_OUTPUT,
        FUME_OUTPUT,
        ASH_OUTPUT,
        OUTPUT,
        UNUSED
    }

    public record SlotPosition(int x, int y, SlotRole role, boolean visible) {
    }
}
