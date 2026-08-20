package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

final class BoundedEntityQuery {
    private BoundedEntityQuery() {}

    static <T extends Entity> List<T> collect(
        final ServerLevel level,
        final Class<T> type,
        final AABB bounds,
        final Predicate<? super T> predicate,
        final int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }
        final var matches = new ArrayList<T>(limit);
        level.getEntities(EntityTypeTest.forClass(type), bounds, predicate, matches, limit);
        return matches;
    }

    static <T extends Entity> boolean any(
        final ServerLevel level,
        final Class<T> type,
        final AABB bounds,
        final Predicate<? super T> predicate
    ) {
        return !collect(level, type, bounds, predicate, 1).isEmpty();
    }
}
