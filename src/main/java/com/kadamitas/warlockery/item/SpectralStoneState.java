package com.kadamitas.warlockery.item;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record SpectralStoneState(List<Identifier> captured) {
    private static final String CAPTURED = "WarlockeryCapturedSpectra";
    public static final int CAPACITY = 3;

    public SpectralStoneState {
        captured = List.copyOf(captured.stream().limit(CAPACITY).toList());
    }

    public static SpectralStoneState read(final ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    public static SpectralStoneState read(final CompoundTag data) {
        final List<Identifier> values = data.getListOrEmpty(CAPTURED).stream()
            .filter(CompoundTag.class::isInstance)
            .map(CompoundTag.class::cast)
            .map(tag -> Identifier.tryParse(tag.getStringOr("type", "")))
            .filter(java.util.Objects::nonNull)
            .limit(CAPACITY)
            .toList();
        return new SpectralStoneState(values);
    }

    public SpectralStoneState with(final Identifier entityType) {
        return new SpectralStoneState(java.util.stream.Stream.concat(captured.stream(), java.util.stream.Stream.of(entityType)).toList());
    }

    public boolean canCapture(final Identifier entityType) {
        return captured.size() < CAPACITY
            && (captured.isEmpty() || captured.getFirst().equals(entityType));
    }

    public SpectralStoneState withoutFirst() {
        return captured.isEmpty() ? this : new SpectralStoneState(captured.subList(1, captured.size()));
    }

    public void write(final ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> write(data));
    }

    public void write(final CompoundTag data) {
        final ListTag values = new ListTag();
        captured.forEach(id -> {
            final CompoundTag entry = new CompoundTag();
            entry.putString("type", id.toString());
            values.add(entry);
        });
        if (values.isEmpty()) {
            data.remove(CAPTURED);
        } else {
            data.put(CAPTURED, values);
        }
    }
}
