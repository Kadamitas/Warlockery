package com.kadamitas.warlockery.item;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record BitingBeltState(Optional<StoredEffect> helpful, Optional<StoredEffect> harmful) {
    private static final String HELPFUL = "WarlockeryBeltHelpful";
    private static final String HARMFUL = "WarlockeryBeltHarmful";
    public static final BitingBeltState EMPTY = new BitingBeltState(Optional.empty(), Optional.empty());

    public BitingBeltState {
        helpful = helpful == null ? Optional.empty() : helpful;
        harmful = harmful == null ? Optional.empty() : harmful;
    }

    public static BitingBeltState read(final ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    static BitingBeltState read(final CompoundTag data) {
        return new BitingBeltState(readEffect(data, HELPFUL), readEffect(data, HARMFUL));
    }

    public void write(final ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, this::write);
    }

    void write(final CompoundTag data) {
        writeEffect(data, HELPFUL, helpful);
        writeEffect(data, HARMFUL, harmful);
    }

    private static Optional<StoredEffect> readEffect(final CompoundTag data, final String key) {
        return data.getCompound(key).flatMap(tag -> {
            final String id = tag.getStringOr("Id", "");
            if (id.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new StoredEffect(
                    Identifier.parse(id),
                    tag.getIntOr("Duration", 200),
                    tag.getIntOr("Amplifier", 0)
                ));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        });
    }

    private static void writeEffect(
        final CompoundTag data,
        final String key,
        final Optional<StoredEffect> effect
    ) {
        if (effect.isEmpty()) {
            data.remove(key);
            return;
        }
        final StoredEffect stored = effect.orElseThrow();
        final CompoundTag value = new CompoundTag();
        value.putString("Id", stored.id().toString());
        value.putInt("Duration", stored.duration());
        value.putInt("Amplifier", stored.amplifier());
        data.put(key, value);
    }

    public record StoredEffect(Identifier id, int duration, int amplifier) {
        public StoredEffect {
            if (duration < 1 || amplifier < 0) {
                throw new IllegalArgumentException("Invalid stored belt effect");
            }
            duration = Math.min(duration, 1_200);
            amplifier = Math.min(amplifier, 4);
        }

        public Optional<MobEffectInstance> resolve() {
            return BuiltInRegistries.MOB_EFFECT.get(id)
                .map(holder -> new MobEffectInstance(holder, Math.min(duration, 240), amplifier, false, true, true));
        }
    }
}
