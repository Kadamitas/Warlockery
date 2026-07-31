package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.brew.BrewKind;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record ArchfiendsUrnState(List<String> brews) {
    private static final String STORED_BREWS = "WarlockeryStoredUrnBrews";
    public static final int CAPACITY = 4;

    public ArchfiendsUrnState {
        brews = List.copyOf(brews.stream()
            .map(value -> Objects.requireNonNull(value, "brew").strip())
            .filter(value -> BrewKind.find(value).isPresent())
            .distinct()
            .limit(CAPACITY)
            .toList());
    }

    public static ArchfiendsUrnState empty() {
        return new ArchfiendsUrnState(List.of());
    }

    public static ArchfiendsUrnState read(final ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    public static ArchfiendsUrnState read(final CompoundTag data) {
        final List<String> brews = data.getListOrEmpty(STORED_BREWS).stream()
            .filter(CompoundTag.class::isInstance)
            .map(CompoundTag.class::cast)
            .map(entry -> entry.getStringOr("brew", ""))
            .toList();
        return new ArchfiendsUrnState(brews);
    }

    public AddResult add(final BrewKind brew) {
        Objects.requireNonNull(brew, "brew");
        if (brews.contains(brew.id())) {
            return new AddResult(this, Diagnostic.ALREADY_STORED);
        }
        if (brews.size() >= CAPACITY) {
            return new AddResult(this, Diagnostic.FULL);
        }
        return new AddResult(
            new ArchfiendsUrnState(java.util.stream.Stream.concat(
                brews.stream(),
                java.util.stream.Stream.of(brew.id())
            ).toList()),
            Diagnostic.STORED
        );
    }

    public List<BrewKind> resolvedBrews() {
        return brews.stream().map(BrewKind::require).toList();
    }

    public void write(final ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, this::write);
    }

    public void write(final CompoundTag data) {
        final ListTag values = new ListTag();
        brews.forEach(brew -> {
            final CompoundTag entry = new CompoundTag();
            entry.putString("brew", brew);
            values.add(entry);
        });
        if (values.isEmpty()) {
            data.remove(STORED_BREWS);
        } else {
            data.put(STORED_BREWS, values);
        }
    }

    public enum Diagnostic {
        STORED,
        ALREADY_STORED,
        FULL
    }

    public record AddResult(ArchfiendsUrnState state, Diagnostic diagnostic) {
        public AddResult {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(diagnostic, "diagnostic");
        }

        public boolean changed() {
            return diagnostic == Diagnostic.STORED;
        }
    }
}
