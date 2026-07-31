package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class MagicMirrorMemory extends SavedData {
    private static final int MAX_VISITORS = 8;
    private static final Codec<MagicMirrorMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Entry.CODEC.listOf().optionalFieldOf("mirrors", List.of()).forGetter(data -> data.entries())
    ).apply(instance, MagicMirrorMemory::new));
    public static final SavedDataType<MagicMirrorMemory> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "magic_mirror_memory"),
        MagicMirrorMemory::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<Long, List<String>> visitors;

    public MagicMirrorMemory() {
        visitors = new HashMap<>();
    }

    private MagicMirrorMemory(final List<Entry> entries) {
        visitors = new HashMap<>();
        entries.forEach(entry -> visitors.put(entry.position(), List.copyOf(entry.visitors())));
    }

    public static MagicMirrorMemory get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<String> record(final BlockPos pos, final String visitor) {
        final List<String> names = new ArrayList<>(visitors.getOrDefault(pos.asLong(), List.of()));
        names.remove(visitor);
        names.addFirst(visitor);
        if (names.size() > MAX_VISITORS) {
            names.subList(MAX_VISITORS, names.size()).clear();
        }
        visitors.put(pos.asLong(), List.copyOf(names));
        setDirty();
        return List.copyOf(names);
    }

    public void remove(final BlockPos pos) {
        if (visitors.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    private List<Entry> entries() {
        return visitors.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue())).toList();
    }

    private record Entry(long position, List<String> visitors) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter(Entry::position),
            Codec.STRING.listOf().fieldOf("visitors").forGetter(Entry::visitors)
        ).apply(instance, Entry::new));
    }
}
