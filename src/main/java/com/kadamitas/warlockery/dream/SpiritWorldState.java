package com.kadamitas.warlockery.dream;

import com.kadamitas.warlockery.util.DataParsing;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class SpiritWorldState {
    private static final String ROOT = "WarlockerySpiritWorldSession";
    private static final String NIGHTMARE = "nightmare";
    private static final String DEMONIC_NIGHTMARE = "demonic_nightmare";
    private static final String SOURCE_DIMENSION = "source_dimension";
    private static final String SOURCE_X = "source_x";
    private static final String SOURCE_Y = "source_y";
    private static final String SOURCE_Z = "source_z";
    private static final String SOURCE_YAW = "source_yaw";
    private static final String SOURCE_PITCH = "source_pitch";
    private static final String BODY = "body";
    private static final String PORTAL = "portal";
    private static final String ORIGINAL_INVENTORY = "original_inventory";
    private static final String SELECTED_SLOT = "selected_slot";
    private static final Codec<List<ItemStackWithSlot>> INVENTORY_CODEC = ItemStackWithSlot.CODEC.listOf();

    private SpiritWorldState() {
    }

    public static boolean active(final net.minecraft.world.entity.player.Player player) {
        return player.getPersistentData().getCompound(ROOT).isPresent();
    }

    public static void begin(final ServerPlayer player, final Session session) {
        final CompoundTag root = new CompoundTag();
        root.putBoolean(NIGHTMARE, session.nightmare());
        root.putBoolean(DEMONIC_NIGHTMARE, session.demonicNightmare());
        root.putString(SOURCE_DIMENSION, session.sourceDimension().toString());
        root.putDouble(SOURCE_X, session.sourceX());
        root.putDouble(SOURCE_Y, session.sourceY());
        root.putDouble(SOURCE_Z, session.sourceZ());
        root.putFloat(SOURCE_YAW, session.sourceYaw());
        root.putFloat(SOURCE_PITCH, session.sourcePitch());
        root.putString(BODY, session.body().toString());
        root.putLong(PORTAL, session.portal().asLong());
        root.putInt(SELECTED_SLOT, session.selectedSlot());
        root.store(
            ORIGINAL_INVENTORY,
            INVENTORY_CODEC,
            player.registryAccess().createSerializationContext(NbtOps.INSTANCE),
            session.originalInventory()
        );
        player.getPersistentData().put(ROOT, root);
    }

    public static Optional<Session> read(final ServerPlayer player) {
        return player.getPersistentData().getCompound(ROOT).flatMap(root -> read(player, root));
    }

    public static void clear(final net.minecraft.world.entity.player.Player player) {
        player.getPersistentData().remove(ROOT);
    }

    public static List<ItemStackWithSlot> snapshot(final Inventory inventory) {
        return java.util.stream.IntStream.range(0, inventory.getContainerSize())
            .mapToObj(slot -> new ItemStackWithSlot(slot, inventory.getItem(slot).copy()))
            .filter(entry -> !entry.stack().isEmpty())
            .toList();
    }

    public static void restore(final Inventory inventory, final List<ItemStackWithSlot> snapshot, final int selectedSlot) {
        inventory.clearContent();
        snapshot.stream()
            .filter(entry -> entry.isValidInContainer(inventory.getContainerSize()))
            .forEach(entry -> inventory.setItem(entry.slot(), entry.stack().copy()));
        inventory.setSelectedSlot(Math.clamp(selectedSlot, 0, Inventory.getSelectionSize() - 1));
        inventory.setChanged();
    }

    public static void copyAfterClone(final PlayerEvent.Clone event) {
        event.getOriginal().getPersistentData().getCompound(ROOT)
            .ifPresent(session -> event.getEntity().getPersistentData().put(ROOT, session.copy()));
    }

    private static Optional<Session> read(final ServerPlayer player, final CompoundTag root) {
        final Identifier sourceDimension = Identifier.tryParse(root.getStringOr(SOURCE_DIMENSION, ""));
        final UUID body = DataParsing.uuid(root.getStringOr(BODY, "")).orElse(null);
        final Optional<List<ItemStackWithSlot>> originalInventory = root.read(
            ORIGINAL_INVENTORY,
            INVENTORY_CODEC,
            player.registryAccess().createSerializationContext(NbtOps.INSTANCE)
        );
        if (sourceDimension == null || body == null || originalInventory.isEmpty() || !root.contains(PORTAL)) {
            return Optional.empty();
        }
        return Optional.of(new Session(
            root.getBooleanOr(NIGHTMARE, false),
            root.getBooleanOr(DEMONIC_NIGHTMARE, false),
            sourceDimension,
            root.getDoubleOr(SOURCE_X, 0.5),
            root.getDoubleOr(SOURCE_Y, 64.0),
            root.getDoubleOr(SOURCE_Z, 0.5),
            root.getFloatOr(SOURCE_YAW, 0.0F),
            root.getFloatOr(SOURCE_PITCH, 0.0F),
            body,
            BlockPos.of(root.getLongOr(PORTAL, BlockPos.ZERO.asLong())),
            List.copyOf(originalInventory.orElseThrow()),
            root.getIntOr(SELECTED_SLOT, 0)
        ));
    }

    public record Session(
        boolean nightmare,
        boolean demonicNightmare,
        Identifier sourceDimension,
        double sourceX,
        double sourceY,
        double sourceZ,
        float sourceYaw,
        float sourcePitch,
        UUID body,
        BlockPos portal,
        List<ItemStackWithSlot> originalInventory,
        int selectedSlot
    ) {
        public Session {
            originalInventory = List.copyOf(originalInventory);
            portal = portal.immutable();
        }
    }
}
