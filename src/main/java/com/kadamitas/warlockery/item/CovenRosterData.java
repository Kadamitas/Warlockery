package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class CovenRosterData extends SavedData {
    private static final Codec<CovenRosterData> CODEC = Entry.CODEC.listOf().xmap(
        CovenRosterData::new,
        CovenRosterData::entries
    );
    public static final SavedDataType<CovenRosterData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "coven_rosters"),
        CovenRosterData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Set<Entry> roster;

    public CovenRosterData() {
        roster = new HashSet<>();
    }

    private CovenRosterData(final List<Entry> entries) {
        roster = new HashSet<>(entries);
    }

    public static CovenRosterData get(final ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void register(final UUID owner, final UUID mage) {
        final Entry assignment = new Entry(owner.toString(), mage.toString());
        if (roster.contains(assignment)) {
            return;
        }
        roster.removeIf(entry -> entry.mage().equals(mage.toString()));
        if (roster.add(assignment)) {
            setDirty();
        }
    }

    public void unregister(final UUID mage) {
        if (roster.removeIf(entry -> entry.mage().equals(mage.toString()))) {
            setDirty();
        }
    }

    public int count(final UUID owner) {
        return (int) roster.stream().filter(entry -> entry.owner().equals(owner.toString())).count();
    }

    private List<Entry> entries() {
        return List.copyOf(roster);
    }

    private record Entry(String owner, String mage) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("owner").forGetter(Entry::owner),
            Codec.STRING.fieldOf("mage").forGetter(Entry::mage)
        ).apply(instance, Entry::new));
    }
}
