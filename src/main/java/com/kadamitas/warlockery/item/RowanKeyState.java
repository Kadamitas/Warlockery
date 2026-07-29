package com.kadamitas.warlockery.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record RowanKeyState(List<Door> doors) {
    private static final String DOORS = "WarlockeryRowanDoors";

    public RowanKeyState {
        doors = List.copyOf(doors);
    }

    public static RowanKeyState read(final ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    static RowanKeyState read(final CompoundTag data) {
        final List<Door> doors = new ArrayList<>();
        data.getList(DOORS).ifPresent(list -> list.stream().filter(CompoundTag.class::isInstance)
            .map(CompoundTag.class::cast).forEach(tag -> tag.getLong("Position").ifPresent(position -> {
                final String dimension = tag.getStringOr("Dimension", "");
                try {
                    doors.add(new Door(Identifier.parse(dimension), BlockPos.of(position)));
                } catch (IllegalArgumentException ignored) {
                }
            })));
        return new RowanKeyState(doors);
    }

    public RowanKeyState bind(final Door door, final int capacity) {
        if (doors.contains(door) || doors.size() >= capacity) {
            return this;
        }
        final List<Door> next = new ArrayList<>(doors);
        next.add(door);
        return new RowanKeyState(next);
    }

    public RowanKeyState merge(final RowanKeyState other, final int capacity) {
        RowanKeyState merged = this;
        for (final Door door : other.doors) {
            merged = merged.bind(door, capacity);
        }
        return merged;
    }

    public boolean opens(final Door door) {
        return doors.contains(door);
    }

    public void write(final ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            final ListTag values = new ListTag();
            doors.forEach(door -> {
                final CompoundTag value = new CompoundTag();
                value.putString("Dimension", door.dimension().toString());
                value.putLong("Position", door.position().asLong());
                values.add(value);
            });
            data.put(DOORS, values);
        });
    }

    public record Door(Identifier dimension, BlockPos position) {
    }
}
