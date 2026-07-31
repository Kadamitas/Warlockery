package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class LeechChestMemory extends SavedData {
    public static final int MAX_SAMPLES = 3;
    private static final String COUNT = "WarlockeryLeechSampleCount";
    private static final Codec<LeechChestMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ChestEntry.CODEC.listOf().optionalFieldOf("chests", List.of()).forGetter(LeechChestMemory::entries)
    ).apply(instance, LeechChestMemory::new));
    public static final SavedDataType<LeechChestMemory> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "leech_chest_memory"),
        LeechChestMemory::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<Long, List<Sample>> samples;

    public LeechChestMemory() {
        samples = new HashMap<>();
    }

    private LeechChestMemory(final List<ChestEntry> entries) {
        samples = new HashMap<>();
        entries.forEach(entry -> samples.put(entry.position(), List.copyOf(entry.samples())));
    }

    public static LeechChestMemory get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void remember(final BlockPos pos, final SympatheticBinding binding) {
        final List<Sample> recent = new ArrayList<>(samples.getOrDefault(pos.asLong(), List.of()));
        recent.removeIf(sample -> sample.targetId().equals(binding.targetId().toString()));
        recent.addFirst(Sample.from(binding));
        if (recent.size() > MAX_SAMPLES) {
            recent.subList(MAX_SAMPLES, recent.size()).clear();
        }
        samples.put(pos.asLong(), List.copyOf(recent));
        setDirty();
    }

    public List<SympatheticBinding> samples(final BlockPos pos) {
        return samples.getOrDefault(pos.asLong(), List.of()).stream()
            .map(Sample::binding)
            .flatMap(Optional::stream)
            .toList();
    }

    public Optional<SympatheticBinding> mostRecentOther(final BlockPos pos, final UUID excluded) {
        return samples(pos).stream().filter(binding -> !binding.targetId().equals(excluded)).findFirst();
    }

    public void clear(final BlockPos pos) {
        if (samples.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public static void writePortable(final ItemStack stack, final List<SympatheticBinding> bindings) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> writePortable(data, bindings));
    }

    public static List<SympatheticBinding> readPortable(final ItemStack stack) {
        return readPortable(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    static void writePortable(final CompoundTag data, final List<SympatheticBinding> bindings) {
        final List<SympatheticBinding> limited = bindings.stream().limit(MAX_SAMPLES).toList();
        data.putInt(COUNT, limited.size());
        for (int index = 0; index < limited.size(); index++) {
            final SympatheticBinding binding = limited.get(index);
            data.putString(key(index, "Uuid"), binding.targetId().toString());
            data.putString(key(index, "Name"), binding.targetName());
            data.putString(key(index, "Type"), binding.targetType());
        }
    }

    static List<SympatheticBinding> readPortable(final CompoundTag data) {
        final int count = Math.clamp(data.getIntOr(COUNT, 0), 0, MAX_SAMPLES);
        final List<SympatheticBinding> bindings = new ArrayList<>(count);
        for (int index = count - 1; index >= 0; index--) {
            try {
                bindings.add(new SympatheticBinding(
                    UUID.fromString(data.getStringOr(key(index, "Uuid"), "")),
                    data.getStringOr(key(index, "Name"), "?"),
                    data.getStringOr(key(index, "Type"), "")
                ));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return List.copyOf(bindings);
    }

    private static String key(final int index, final String suffix) {
        return "WarlockeryLeechSample" + index + suffix;
    }

    private List<ChestEntry> entries() {
        return samples.entrySet().stream().map(entry -> new ChestEntry(entry.getKey(), entry.getValue())).toList();
    }

    private record ChestEntry(long position, List<Sample> samples) {
        private static final Codec<ChestEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter(ChestEntry::position),
            Sample.CODEC.listOf().fieldOf("samples").forGetter(ChestEntry::samples)
        ).apply(instance, ChestEntry::new));
    }

    private record Sample(String targetId, String targetName, String targetType) {
        private static final Codec<Sample> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("target_id").forGetter(Sample::targetId),
            Codec.STRING.fieldOf("target_name").forGetter(Sample::targetName),
            Codec.STRING.optionalFieldOf("target_type", "").forGetter(Sample::targetType)
        ).apply(instance, Sample::new));

        private static Sample from(final SympatheticBinding binding) {
            return new Sample(binding.targetId().toString(), binding.targetName(), binding.targetType());
        }

        private Optional<SympatheticBinding> binding() {
            try {
                return Optional.of(new SympatheticBinding(UUID.fromString(targetId), targetName, targetType));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }
}
