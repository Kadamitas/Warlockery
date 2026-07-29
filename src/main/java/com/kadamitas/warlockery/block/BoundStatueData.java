package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class BoundStatueData extends SavedData {
    private static final Codec<BoundStatueData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Entry.CODEC.listOf().optionalFieldOf("bindings", List.of()).forGetter(data -> List.copyOf(data.bindings.values()))
    ).apply(instance, BoundStatueData::new));
    public static final SavedDataType<BoundStatueData> TYPE = new SavedDataType<>(
        Identifier.parse(Warlockery.MOD_ID + ":bound_statues"),
        BoundStatueData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<Long, Entry> bindings;

    public BoundStatueData() {
        bindings = new HashMap<>();
    }

    private BoundStatueData(final List<Entry> entries) {
        bindings = new HashMap<>();
        entries.forEach(entry -> bindings.put(entry.position(), entry));
    }

    public static BoundStatueData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void bind(final BlockPos pos, final SympatheticBinding binding) {
        bindings.put(pos.asLong(), Entry.from(pos, binding));
        setDirty();
    }

    public Optional<SympatheticBinding> binding(final BlockPos pos) {
        return Optional.ofNullable(bindings.get(pos.asLong())).flatMap(Entry::binding);
    }

    public void remove(final BlockPos pos) {
        if (bindings.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public void tick(final ServerLevel level) {
        if (level.getGameTime() % 200L != 0L) {
            return;
        }
        final boolean removed = bindings.entrySet().removeIf(entry -> {
            final BlockPos pos = BlockPos.of(entry.getKey());
            return level.isLoaded(pos) && (!(level.getBlockState(pos).getBlock() instanceof StatueBlock statue)
                || statue.profile().effect() != StatueProfile.Effect.PATRON_BLESSING);
        });
        if (removed) {
            setDirty();
        }
    }

    private record Entry(long position, String targetId, String targetName, String targetType) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter(Entry::position),
            Codec.STRING.fieldOf("target_id").forGetter(Entry::targetId),
            Codec.STRING.fieldOf("target_name").forGetter(Entry::targetName),
            Codec.STRING.optionalFieldOf("target_type", "").forGetter(Entry::targetType)
        ).apply(instance, Entry::new));

        private static Entry from(final BlockPos pos, final SympatheticBinding binding) {
            return new Entry(pos.asLong(), binding.targetId().toString(), binding.targetName(), binding.targetType());
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
