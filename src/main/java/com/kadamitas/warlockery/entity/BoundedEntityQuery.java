package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

public final class BoundedEntityQuery {
    private BoundedEntityQuery() {}

    public static <T extends Entity> List<T> collect(
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

    public static <T extends Entity> boolean any(
        final ServerLevel level,
        final Class<T> type,
        final AABB bounds,
        final Predicate<? super T> predicate
    ) {
        return !collect(level, type, bounds, predicate, 1).isEmpty();
    }

    /**
     * Loader-neutral abortable traversal. Fabric exposes a bounded predicate/output overload rather
     * than Forge's entity-section accessor; accepting the aborting entity into a one-element sink
     * makes the public overload stop at the same point without retaining every visited entity.
     */
    public static <T extends Entity> void visit(
        final ServerLevel level,
        final EntityTypeTest<Entity, T> type,
        final AABB bounds,
        final net.minecraft.util.AbortableIterationConsumer<? super T> visitor
    ) {
        final var stop = new ArrayList<T>(1);
        level.getEntities(type, bounds, entity -> visitor.accept(entity).shouldAbort(), stop, 1);
    }
}
