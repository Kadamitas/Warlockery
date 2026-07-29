package com.kadamitas.warlockery.crafting;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.Direction;

public record MachineSlotLayout(List<Integer> inputs, List<Integer> outputs, List<Integer> fuel) {
    public MachineSlotLayout {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        fuel = List.copyOf(fuel);
    }

    public static MachineSlotLayout create(final MachineProfile profile, final int inventorySize) {
        final List<Integer> inputs = IntStream.range(0, profile.inputSlots()).boxed().toList();
        final List<Integer> outputs = IntStream.range(profile.outputStart(), inventorySize).boxed().toList();
        final List<Integer> fuel = profile.hasFuelSlot() ? List.of(profile.fuelSlot()) : List.of();
        return new MachineSlotLayout(inputs, outputs, fuel);
    }

    public int[] slotsFor(final Direction face) {
        return switch (face) {
            case UP -> toArray(inputs.stream());
            case DOWN -> toArray(Stream.concat(outputs.stream(), fuel.stream()));
            default -> toArray((fuel.isEmpty() ? inputs : fuel).stream());
        };
    }

    public boolean accepts(final Direction face, final int slot) {
        return switch (face) {
            case UP -> inputs.contains(slot);
            case DOWN -> false;
            default -> (fuel.isEmpty() ? inputs : fuel).contains(slot);
        };
    }

    public boolean extractsOutput(final Direction face, final int slot) {
        return face == Direction.DOWN && outputs.contains(slot);
    }

    public boolean extractsFuelRemainder(final Direction face, final int slot) {
        return face == Direction.DOWN && fuel.contains(slot);
    }

    private static int[] toArray(final Stream<Integer> slots) {
        return slots.mapToInt(Integer::intValue).toArray();
    }
}
