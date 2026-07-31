package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

public final class VoidBrambleOwnershipData extends SavedData {
    private static final Codec<VoidBrambleOwnershipData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Entry.CODEC.listOf().optionalFieldOf("owners", List.of()).forGetter(VoidBrambleOwnershipData::entries)
    ).apply(instance, VoidBrambleOwnershipData::new));
    public static final SavedDataType<VoidBrambleOwnershipData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "void_bramble_owners"),
        VoidBrambleOwnershipData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<Long, UUID> owners;

    public VoidBrambleOwnershipData() {
        owners = new HashMap<>();
    }

    private VoidBrambleOwnershipData(final List<Entry> entries) {
        owners = new HashMap<>();
        entries.forEach(entry -> entry.ownerId().ifPresent(owner -> owners.put(entry.position(), owner)));
    }

    public static VoidBrambleOwnershipData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void claim(final BlockPos pos, final UUID owner) {
        owners.put(pos.asLong(), owner);
        setDirty();
    }

    public Optional<UUID> owner(final BlockPos pos) {
        return Optional.ofNullable(owners.get(pos.asLong()));
    }

    public boolean permits(final BlockPos pos, final ServerPlayer player) {
        return VoidBrambleRules.canBreak(owners.get(pos.asLong()), player.getUUID(), player.hasInfiniteMaterials());
    }

    public void remove(final BlockPos pos) {
        if (owners.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public static void handleBreak(final BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || !(event.getPlayer() instanceof ServerPlayer player)
            || !(event.getState().getBlock() instanceof VoidBrambleBlock)) {
            return;
        }
        final VoidBrambleOwnershipData data = get(level);
        if (!data.permits(event.getPos(), player)) {
            event.setCanceled(true);
            event.setNotifyClient(true);
            player.sendOverlayMessage(Component.translatable("message.warlockery.void_bramble.owner_only"));
            return;
        }
        data.remove(event.getPos());
    }

    private List<Entry> entries() {
        return owners.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue().toString())).toList();
    }

    private record Entry(long position, String owner) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter(Entry::position),
            Codec.STRING.fieldOf("owner").forGetter(Entry::owner)
        ).apply(instance, Entry::new));

        private Optional<UUID> ownerId() {
            try {
                return Optional.of(UUID.fromString(owner));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }
}
