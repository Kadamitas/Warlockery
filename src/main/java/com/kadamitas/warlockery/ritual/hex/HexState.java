package com.kadamitas.warlockery.ritual.hex;

import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class HexState {
    private static final String ACTIVE_HEXES = "WarlockeryActiveHexes";

    private HexState() {
    }

    public static void apply(final LivingEntity target, final HexKind kind, final int durationTicks) {
        final CompoundTag active = activeTag(target, true).orElseThrow();
        final long expiration = target.level().getGameTime() + Math.max(1, durationTicks);
        active.putLong(kind.id(), Math.max(expiration, active.getLongOr(kind.id(), 0L)));
    }

    public static boolean isActive(final LivingEntity target, final HexKind kind) {
        return remainingTicks(target, kind) > 0;
    }

    public static int remainingTicks(final LivingEntity target, final HexKind kind) {
        return activeTag(target, false)
            .map(active -> active.getLongOr(kind.id(), 0L) - target.level().getGameTime())
            .filter(remaining -> remaining > 0L)
            .map(remaining -> (int) Math.min(Integer.MAX_VALUE, remaining))
            .orElse(0);
    }

    public static List<ActiveHex> active(final LivingEntity target) {
        return Arrays.stream(HexKind.values())
            .map(kind -> new ActiveHex(kind, remainingTicks(target, kind)))
            .filter(hex -> hex.remainingTicks() > 0)
            .toList();
    }

    public static List<HexKind> removeExpired(final LivingEntity target) {
        final long gameTime = target.level().getGameTime();
        final List<HexKind> expired = activeTag(target, false).stream()
            .flatMap(active -> Arrays.stream(HexKind.values())
                .filter(kind -> {
                    final long expiration = active.getLongOr(kind.id(), 0L);
                    return expiration > 0L && expiration <= gameTime;
                }))
            .toList();
        expired.forEach(kind -> remove(target, kind));
        return expired;
    }

    public static void remove(final LivingEntity target, final HexKind kind) {
        activeTag(target, false).ifPresent(active -> {
            active.remove(kind.id());
            if (active.isEmpty()) {
                target.getPersistentData().remove(ACTIVE_HEXES);
            }
        });
    }

    public static void copyAfterClone(final PlayerEvent.Clone event) {
        Arrays.stream(HexKind.values()).forEach(kind -> {
            final int remaining = remainingTicks(event.getOriginal(), kind);
            if (remaining > 0) {
                apply(event.getEntity(), kind, remaining);
            }
        });
    }

    private static java.util.Optional<CompoundTag> activeTag(
        final LivingEntity target,
        final boolean create
    ) {
        final CompoundTag data = target.getPersistentData();
        final java.util.Optional<CompoundTag> existing = data.getCompound(ACTIVE_HEXES);
        if (existing.isPresent() || !create) {
            return existing;
        }
        final CompoundTag active = new CompoundTag();
        data.put(ACTIVE_HEXES, active);
        return java.util.Optional.of(active);
    }

    public record ActiveHex(HexKind kind, int remainingTicks) {
    }
}
