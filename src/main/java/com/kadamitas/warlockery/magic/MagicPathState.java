package com.kadamitas.warlockery.magic;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class MagicPathState {
    private static final String ROOT = "WarlockeryMagicPaths";
    private static final String PERMANENT = "permanent";
    private static final String EXPIRATION = "expiration";
    private static final String RESERVE = "reserve";
    private static final String SELECTED = "selected";
    private static final String LAST_POWER = "last_power";
    private static final String RECALL_DIMENSION = "recall_dimension";
    private static final String RECALL_POSITION = "recall_position";

    private MagicPathState() {
    }

    public static void grantPermanent(final Player player, final MagicPath path) {
        final CompoundTag state = pathState(player.getPersistentData(), path, true).orElseThrow();
        state.putBoolean(PERMANENT, true);
        state.putInt(RESERVE, path.maximumReserve());
        root(player.getPersistentData(), true).orElseThrow().putString(SELECTED, path.id());
    }

    public static void grantTimed(final Player player, final MagicPath path, final int durationTicks) {
        final CompoundTag state = pathState(player.getPersistentData(), path, true).orElseThrow();
        final long expiration = player.level().getGameTime() + Math.max(1, durationTicks);
        state.putLong(EXPIRATION, Math.max(expiration, state.getLongOr(EXPIRATION, 0L)));
        if (!state.contains(RESERVE)) {
            state.putInt(RESERVE, path.maximumReserve());
        }
    }

    public static boolean has(final Player player, final MagicPath path) {
        return has(player.getPersistentData(), path, player.level().getGameTime());
    }

    public static boolean has(final CompoundTag data, final MagicPath path, final long gameTime) {
        return pathState(data, path, false)
            .map(state -> state.getBooleanOr(PERMANENT, false)
                || state.getLongOr(EXPIRATION, 0L) > gameTime)
            .orElse(false);
    }

    public static int reserve(final Player player, final MagicPath path) {
        return pathState(player.getPersistentData(), path, false)
            .map(state -> Math.clamp(state.getIntOr(RESERVE, 0), 0, path.maximumReserve()))
            .orElse(0);
    }

    public static boolean spend(final Player player, final MagicPath path, final int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Reserve cost must be nonnegative");
        }
        final int current = reserve(player, path);
        if (!has(player, path) || current < amount) {
            return false;
        }
        pathState(player.getPersistentData(), path, true).orElseThrow().putInt(RESERVE, current - amount);
        return true;
    }

    public static int recharge(final Player player, final MagicPath path, final int amount) {
        final CompoundTag state = pathState(player.getPersistentData(), path, true).orElseThrow();
        final int next = MagicPathRules.adjustedReserve(reserve(player, path), amount, path.maximumReserve());
        state.putInt(RESERVE, next);
        return next;
    }

    public static List<MagicPath> active(final Player player) {
        return Arrays.stream(MagicPath.values()).filter(path -> has(player, path)).toList();
    }

    public static Optional<MagicPath> selected(final Player player) {
        final List<MagicPath> active = active(player);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        final String selected = root(player.getPersistentData(), false)
            .map(tag -> tag.getStringOr(SELECTED, ""))
            .orElse("");
        return MagicPath.find(selected).filter(active::contains).or(() -> Optional.of(active.getFirst()));
    }

    public static Optional<MagicPath> cycle(final Player player) {
        final List<MagicPath> active = active(player);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        final MagicPath current = selected(player).orElse(active.getFirst());
        final MagicPath next = active.get((active.indexOf(current) + 1) % active.size());
        root(player.getPersistentData(), true).orElseThrow().putString(SELECTED, next.id());
        return Optional.of(next);
    }

    public static void setLastPower(final Player player, final InfernalPower power) {
        root(player.getPersistentData(), true).orElseThrow().putString(LAST_POWER, power.id());
    }

    public static InfernalPower lastPower(final Player player) {
        final String id = root(player.getPersistentData(), false)
            .map(tag -> tag.getStringOr(LAST_POWER, InfernalPower.HEALING.id()))
            .orElse(InfernalPower.HEALING.id());
        return InfernalPower.find(id).orElse(InfernalPower.HEALING);
    }

    public static void setRecall(final Player player, final Identifier dimension, final BlockPos position) {
        final CompoundTag root = root(player.getPersistentData(), true).orElseThrow();
        root.putString(RECALL_DIMENSION, dimension.toString());
        root.putLong(RECALL_POSITION, position.asLong());
    }

    public static Optional<Recall> recall(final Player player) {
        return root(player.getPersistentData(), false).flatMap(root -> {
            final Identifier dimension = Identifier.tryParse(root.getStringOr(RECALL_DIMENSION, ""));
            if (dimension == null || !root.contains(RECALL_POSITION)) {
                return Optional.empty();
            }
            return Optional.of(new Recall(dimension, BlockPos.of(root.getLongOr(RECALL_POSITION, BlockPos.ZERO.asLong()))));
        });
    }

    public static void copyAfterClone(final PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            return;
        }
        event.getOriginal().getPersistentData().getCompound(ROOT)
            .ifPresent(state -> event.getEntity().getPersistentData().put(ROOT, state.copy()));
    }

    static Optional<CompoundTag> root(final CompoundTag data, final boolean create) {
        final Optional<CompoundTag> existing = data.getCompound(ROOT);
        if (existing.isPresent() || !create) {
            return existing;
        }
        final CompoundTag root = new CompoundTag();
        data.put(ROOT, root);
        return Optional.of(root);
    }

    static Optional<CompoundTag> pathState(final CompoundTag data, final MagicPath path, final boolean create) {
        final Optional<CompoundTag> root = root(data, create);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        final Optional<CompoundTag> existing = root.orElseThrow().getCompound(path.id());
        if (existing.isPresent() || !create) {
            return existing;
        }
        final CompoundTag state = new CompoundTag();
        root.orElseThrow().put(path.id(), state);
        return Optional.of(state);
    }

    public record Recall(Identifier dimension, BlockPos position) {
    }
}
