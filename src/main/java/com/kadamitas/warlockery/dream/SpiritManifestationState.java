package com.kadamitas.warlockery.dream;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class SpiritManifestationState {
    private static final String ROOT = "WarlockerySpiritManifestation";
    private static final String EXPIRATION = "expiration";
    private static final String ACTIVE = "active";
    private static final String RETURN_DIMENSION = "return_dimension";
    private static final String RETURN_X = "return_x";
    private static final String RETURN_Y = "return_y";
    private static final String RETURN_Z = "return_z";
    private static final String RETURN_YAW = "return_yaw";
    private static final String RETURN_PITCH = "return_pitch";
    private static final String STORED_INVENTORY = "stored_inventory";
    private static final String SELECTED_SLOT = "selected_slot";
    private static final Codec<List<ItemStackWithSlot>> INVENTORY_CODEC = ItemStackWithSlot.CODEC.listOf();

    private SpiritManifestationState() {
    }

    public static void grant(final ServerPlayer player, final long expiration) {
        final CompoundTag root = root(player);
        root.putLong(EXPIRATION, SpiritManifestationRules.extend(
            root.getLongOr(EXPIRATION, 0L),
            expiration
        ));
        player.getPersistentData().put(ROOT, root);
    }

    public static boolean granted(final ServerPlayer player, final long serverTick) {
        return player.getPersistentData().getCompound(ROOT)
            .map(root -> !SpiritManifestationRules.expired(serverTick, root.getLongOr(EXPIRATION, 0L)))
            .orElse(false);
    }

    public static long expiration(final ServerPlayer player) {
        return player.getPersistentData().getCompound(ROOT)
            .map(root -> root.getLongOr(EXPIRATION, 0L))
            .orElse(0L);
    }

    public static boolean active(final ServerPlayer player) {
        return player.getPersistentData().getCompound(ROOT)
            .map(root -> root.getBooleanOr(ACTIVE, false))
            .orElse(false);
    }

    public static void begin(
        final ServerPlayer player,
        final Identifier returnDimension,
        final double returnX,
        final double returnY,
        final double returnZ,
        final float returnYaw,
        final float returnPitch,
        final List<ItemStackWithSlot> storedInventory,
        final int selectedSlot
    ) {
        final CompoundTag root = root(player);
        root.putBoolean(ACTIVE, true);
        root.putString(RETURN_DIMENSION, returnDimension.toString());
        root.putDouble(RETURN_X, returnX);
        root.putDouble(RETURN_Y, returnY);
        root.putDouble(RETURN_Z, returnZ);
        root.putFloat(RETURN_YAW, returnYaw);
        root.putFloat(RETURN_PITCH, returnPitch);
        root.putInt(SELECTED_SLOT, selectedSlot);
        root.store(
            STORED_INVENTORY,
            INVENTORY_CODEC,
            player.registryAccess().createSerializationContext(NbtOps.INSTANCE),
            List.copyOf(storedInventory)
        );
        player.getPersistentData().put(ROOT, root);
    }

    public static Optional<ActiveManifestation> read(final ServerPlayer player) {
        return player.getPersistentData().getCompound(ROOT).flatMap(root -> {
            if (!root.getBooleanOr(ACTIVE, false)) {
                return Optional.empty();
            }
            final Identifier dimension = Identifier.tryParse(root.getStringOr(RETURN_DIMENSION, ""));
            final Optional<List<ItemStackWithSlot>> inventory = root.read(
                STORED_INVENTORY,
                INVENTORY_CODEC,
                player.registryAccess().createSerializationContext(NbtOps.INSTANCE)
            );
            if (dimension == null || inventory.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ActiveManifestation(
                root.getLongOr(EXPIRATION, 0L),
                dimension,
                root.getDoubleOr(RETURN_X, 0.5),
                root.getDoubleOr(RETURN_Y, 64.0),
                root.getDoubleOr(RETURN_Z, 0.5),
                root.getFloatOr(RETURN_YAW, 0.0F),
                root.getFloatOr(RETURN_PITCH, 0.0F),
                inventory.orElseThrow(),
                root.getIntOr(SELECTED_SLOT, 0)
            ));
        });
    }

    public static void finish(final ServerPlayer player) {
        player.getPersistentData().getCompound(ROOT).ifPresent(root -> {
            root.putBoolean(ACTIVE, false);
            root.remove(RETURN_DIMENSION);
            root.remove(RETURN_X);
            root.remove(RETURN_Y);
            root.remove(RETURN_Z);
            root.remove(RETURN_YAW);
            root.remove(RETURN_PITCH);
            root.remove(STORED_INVENTORY);
            root.remove(SELECTED_SLOT);
            player.getPersistentData().put(ROOT, root);
        });
    }

    public static void clear(final ServerPlayer player) {
        player.getPersistentData().remove(ROOT);
    }

    public static void copyAfterClone(final PlayerEvent.Clone event) {
        event.getOriginal().getPersistentData().getCompound(ROOT)
            .ifPresent(root -> event.getEntity().getPersistentData().put(ROOT, root.copy()));
    }

    private static CompoundTag root(final ServerPlayer player) {
        return player.getPersistentData().getCompound(ROOT).map(CompoundTag::copy).orElseGet(CompoundTag::new);
    }

    public record ActiveManifestation(
        long expiration,
        Identifier returnDimension,
        double returnX,
        double returnY,
        double returnZ,
        float returnYaw,
        float returnPitch,
        List<ItemStackWithSlot> storedInventory,
        int selectedSlot
    ) {
        public ActiveManifestation {
            storedInventory = List.copyOf(storedInventory);
        }
    }
}
