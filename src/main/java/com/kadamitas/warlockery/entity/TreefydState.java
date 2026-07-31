package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.item.SympatheticBinding;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

public final class TreefydState {
    public static final int MAX_ALLOWLIST = 8;
    private static final String WANDERING = "WarlockeryTreefydWandering";

    private TreefydState() {
    }

    public static boolean toggleAllowed(final Entity treefyd, final SympatheticBinding binding) {
        final int existing = indexOf(treefyd, binding.targetId());
        if (existing >= 0) {
            clearSlot(treefyd, existing);
            compact(treefyd);
            return false;
        }
        final int empty = firstEmpty(treefyd);
        if (empty < 0) {
            return false;
        }
        treefyd.getPersistentData().putString(key(empty, "Uuid"), binding.targetId().toString());
        treefyd.getPersistentData().putString(key(empty, "Name"), binding.targetName());
        return true;
    }

    public static boolean isAllowed(final Entity treefyd, final UUID target) {
        return indexOf(treefyd, target) >= 0;
    }

    public static boolean toggleWandering(final Entity treefyd) {
        final boolean next = !wandering(treefyd);
        treefyd.getPersistentData().putBoolean(WANDERING, next);
        return next;
    }

    public static boolean wandering(final Entity treefyd) {
        return !treefyd.getPersistentData().contains(WANDERING)
            || treefyd.getPersistentData().getBooleanOr(WANDERING, true);
    }

    private static int indexOf(final Entity treefyd, final UUID target) {
        for (int index = 0; index < MAX_ALLOWLIST; index++) {
            if (target.toString().equals(treefyd.getPersistentData().getStringOr(key(index, "Uuid"), ""))) {
                return index;
            }
        }
        return -1;
    }

    private static int firstEmpty(final Entity treefyd) {
        for (int index = 0; index < MAX_ALLOWLIST; index++) {
            if (treefyd.getPersistentData().getStringOr(key(index, "Uuid"), "").isBlank()) {
                return index;
            }
        }
        return -1;
    }

    private static void compact(final Entity treefyd) {
        int write = 0;
        for (int read = 0; read < MAX_ALLOWLIST; read++) {
            final String uuid = treefyd.getPersistentData().getStringOr(key(read, "Uuid"), "");
            if (uuid.isBlank()) {
                continue;
            }
            final String name = treefyd.getPersistentData().getStringOr(key(read, "Name"), "?");
            if (write != read) {
                treefyd.getPersistentData().putString(key(write, "Uuid"), uuid);
                treefyd.getPersistentData().putString(key(write, "Name"), name);
                clearSlot(treefyd, read);
            }
            write++;
        }
    }

    private static void clearSlot(final Entity treefyd, final int index) {
        treefyd.getPersistentData().remove(key(index, "Uuid"));
        treefyd.getPersistentData().remove(key(index, "Name"));
    }

    private static String key(final int index, final String suffix) {
        return "WarlockeryTreefydAllowed" + index + suffix;
    }
}
