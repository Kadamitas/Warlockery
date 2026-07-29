package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class BrewMarkerState {
    private static final String ACTIVE_MARKERS = "WarlockeryBrewMarkers";
    private static final String EXPIRATION = "expiration";
    private static final String ABSORBED_MAGIC = "absorbed_magic";
    private static final String SAVED_ITEMS = "saved_items";
    private static final String SAVED_EFFECTS = "saved_effects";
    private static final String LOCKED_FORM = "locked_form";
    private static final String ORIGIN = "origin";
    private static final String COLOR = "color";

    private BrewMarkerState() {
    }

    public static CompoundTag apply(final LivingEntity target, final BrewMarkerKind kind) {
        return apply(target, kind, kind.defaultDuration());
    }

    public static CompoundTag apply(
        final LivingEntity target,
        final BrewMarkerKind kind,
        final int durationTicks
    ) {
        final CompoundTag marker = marker(target, kind, true).orElseThrow();
        final long expiration = target.level().getGameTime() + Math.max(1, durationTicks);
        marker.putLong(EXPIRATION, Math.max(expiration, marker.getLongOr(EXPIRATION, 0L)));
        return marker;
    }

    public static boolean isActive(final LivingEntity target, final BrewMarkerKind kind) {
        return remainingTicks(target, kind) > 0;
    }

    public static int remainingTicks(final LivingEntity target, final BrewMarkerKind kind) {
        return marker(target, kind, false)
            .map(value -> BrewMarkerRules.remainingTicks(
                target.level().getGameTime(),
                value.getLongOr(EXPIRATION, 0L)
            ))
            .orElse(0);
    }

    public static List<BrewMarkerKind> active(final LivingEntity target) {
        return Arrays.stream(BrewMarkerKind.values())
            .filter(kind -> isActive(target, kind))
            .toList();
    }

    public static List<BrewMarkerKind> removeExpired(final LivingEntity target) {
        final List<BrewMarkerKind> expired = Arrays.stream(BrewMarkerKind.values())
            .filter(kind -> marker(target, kind, false)
                .map(value -> value.getLongOr(EXPIRATION, 0L))
                .filter(expiration -> expiration > 0L)
                .filter(expiration -> !BrewMarkerRules.isActive(target.level().getGameTime(), expiration))
                .isPresent())
            .toList();
        expired.forEach(kind -> remove(target, kind));
        return expired;
    }

    public static void remove(final LivingEntity target, final BrewMarkerKind kind) {
        root(target, false).ifPresent(active -> {
            active.remove(kind.id());
            if (active.isEmpty()) {
                target.getPersistentData().remove(ACTIVE_MARKERS);
            }
        });
    }

    public static int absorbedMagic(final LivingEntity target) {
        return marker(target, BrewMarkerKind.ABSORB_MAGIC, false)
            .map(value -> value.getIntOr(ABSORBED_MAGIC, 0))
            .orElse(0);
    }

    public static int addAbsorbedMagic(final LivingEntity target, final float preventedDamage) {
        final CompoundTag marker = marker(target, BrewMarkerKind.ABSORB_MAGIC, false).orElseThrow();
        final int amount = BrewMarkerRules.addAbsorbedMagic(
            marker.getIntOr(ABSORBED_MAGIC, 0),
            preventedDamage
        );
        marker.putInt(ABSORBED_MAGIC, amount);
        return amount;
    }

    public static int consumeAbsorbedMagic(final LivingEntity target, final int requested) {
        final Optional<CompoundTag> marker = marker(target, BrewMarkerKind.ABSORB_MAGIC, false);
        if (marker.isEmpty() || requested <= 0) {
            return 0;
        }
        final int available = marker.orElseThrow().getIntOr(ABSORBED_MAGIC, 0);
        final int consumed = Math.min(available, requested);
        marker.orElseThrow().putInt(ABSORBED_MAGIC, available - consumed);
        return consumed;
    }

    public static void setOrigin(final LivingEntity target, final BrewMarkerKind kind, final BlockPos origin) {
        apply(target, kind).putLong(ORIGIN, origin.asLong());
    }

    public static Optional<BlockPos> origin(final LivingEntity target, final BrewMarkerKind kind) {
        return marker(target, kind, false)
            .filter(value -> value.contains(ORIGIN))
            .map(value -> BlockPos.of(value.getLongOr(ORIGIN, BlockPos.ZERO.asLong())));
    }

    public static void setColor(final LivingEntity target, final BrewMarkerKind kind, final int color) {
        apply(target, kind).putInt(COLOR, color & 0xFFFFFF);
    }

    public static int color(final LivingEntity target, final BrewMarkerKind kind, final int fallback) {
        return marker(target, kind, false)
            .filter(value -> value.contains(COLOR))
            .map(value -> value.getIntOr(COLOR, fallback) & 0xFFFFFF)
            .orElse(fallback & 0xFFFFFF);
    }

    public static void storeItems(final LivingEntity target, final List<ItemStack> items) {
        final CompoundTag marker = apply(target, BrewMarkerKind.KEEP_INVENTORY);
        marker.store(
            SAVED_ITEMS,
            ItemStack.CODEC.listOf(),
            target.registryAccess().createSerializationContext(NbtOps.INSTANCE),
            List.copyOf(items)
        );
    }

    public static List<ItemStack> savedItems(final LivingEntity target) {
        return marker(target, BrewMarkerKind.KEEP_INVENTORY, false)
            .flatMap(marker -> marker.read(
                SAVED_ITEMS,
                ItemStack.CODEC.listOf(),
                target.registryAccess().createSerializationContext(NbtOps.INSTANCE)
            ))
            .orElse(List.of());
    }

    public static void storeEffects(final LivingEntity target, final List<MobEffectInstance> effects) {
        final CompoundTag marker = apply(target, BrewMarkerKind.KEEP_EFFECTS);
        marker.store(
            SAVED_EFFECTS,
            MobEffectInstance.CODEC.listOf(),
            target.registryAccess().createSerializationContext(NbtOps.INSTANCE),
            effects.stream().map(MobEffectInstance::new).toList()
        );
    }

    public static List<MobEffectInstance> savedEffects(final LivingEntity target) {
        return marker(target, BrewMarkerKind.KEEP_EFFECTS, false)
            .flatMap(marker -> marker.read(
                SAVED_EFFECTS,
                MobEffectInstance.CODEC.listOf(),
                target.registryAccess().createSerializationContext(NbtOps.INSTANCE)
            ))
            .orElse(List.of());
    }

    public static void lockCurrentForm(final Player player) {
        apply(player, BrewMarkerKind.WEREWOLF_LOCK)
            .putString(LOCKED_FORM, SupernaturalState.getForm(player).name());
    }

    public static Optional<SupernaturalForm> lockedForm(final Player player) {
        return marker(player, BrewMarkerKind.WEREWOLF_LOCK, false)
            .map(value -> value.getStringOr(LOCKED_FORM, ""))
            .filter(value -> !value.isBlank())
            .flatMap(value -> {
                try {
                    return Optional.of(SupernaturalForm.valueOf(value));
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            });
    }

    public static Optional<CompoundTag> data(final LivingEntity target, final BrewMarkerKind kind) {
        return marker(target, kind, false);
    }

    public static void copyActive(final LivingEntity source, final LivingEntity target) {
        active(source).forEach(kind -> marker(source, kind, false).ifPresent(sourceMarker -> {
            final CompoundTag copied = sourceMarker.copy();
            copied.putLong(EXPIRATION, target.level().getGameTime() + remainingTicks(source, kind));
            root(target, true).orElseThrow().put(kind.id(), copied);
        }));
    }

    private static Optional<CompoundTag> marker(
        final LivingEntity target,
        final BrewMarkerKind kind,
        final boolean create
    ) {
        final Optional<CompoundTag> active = root(target, create);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        final Optional<CompoundTag> existing = active.orElseThrow().getCompound(kind.id());
        if (existing.isPresent() || !create) {
            return existing;
        }
        final CompoundTag marker = new CompoundTag();
        active.orElseThrow().put(kind.id(), marker);
        return Optional.of(marker);
    }

    private static Optional<CompoundTag> root(final LivingEntity target, final boolean create) {
        final CompoundTag data = target.getPersistentData();
        final Optional<CompoundTag> existing = data.getCompound(ACTIVE_MARKERS);
        if (existing.isPresent() || !create) {
            return existing;
        }
        final CompoundTag active = new CompoundTag();
        data.put(ACTIVE_MARKERS, active);
        return Optional.of(active);
    }
}
