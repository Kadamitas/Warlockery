package com.kadamitas.warlockery.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

public final class LivingRootsRules {
    public static final int ATTRIBUTION_FRESHNESS_TICKS = 40;
    public static final int MAX_CADENCE_SENTINEL = 20_000;
    private static final WeakHashMap<ServerLevel, Quota> QUOTAS = new WeakHashMap<>();

    private LivingRootsRules() {}

    public static boolean rooted(final Enum<?> phase) {
        return phase == MandrakeRules.Phase.SEEDED || phase == MandrakeRules.Phase.RESETTLE
            || phase == DreamrootRules.Phase.ROOTED || phase == DreamrootRules.Phase.SUBSIDE;
    }

    public static int decrementLoaded(final int remaining) { return Math.max(0, remaining - 1); }
    public static boolean zeroIsDue(final long value) { return value <= 0L; }
    public static boolean fresh(final long age) { return age >= 0L && age <= ATTRIBUTION_FRESHNESS_TICKS; }
    public static boolean staggeredDue(final int tickCount, final int entityId, final int cadence) {
        return cadence > 0 && Math.floorMod(tickCount + entityId, cadence) == 0;
    }

    public static Quota quota(final ServerLevel level) {
        if (!level.getServer().isSameThread()) return Quota.denied();
        final int tick = level.getServer().getTickCount();
        final Quota quota = QUOTAS.computeIfAbsent(level, ignored -> new Quota());
        quota.reset(tick);
        return quota;
    }

    public record SafeObservation(boolean safe, int actualReads) {}

    public static SafeObservation observeSafeDestination(final ServerLevel level, final Entity body,
            final BlockPos destination, final int readBudget, final Predicate<BlockState> contactHazard) {
        final AABB moved = body.getBoundingBox().move(destination.getX() + 0.5D - body.getX(),
            destination.getY() - body.getY(), destination.getZ() + 0.5D - body.getZ());
        final HaloReadCache reads = new HaloReadCache(level, moved.inflate(1.0D), readBudget);
        if (!reads.ready() || !level.getWorldBorder().isWithinBounds(destination)) return new SafeObservation(false, 0);
        final BlockPos supportPos = destination.below();
        final BlockState support = reads.getBlockState(supportPos);
        final BlockState feet = reads.getBlockState(destination);
        final BlockState head = reads.getBlockState(destination.above());
        boolean clear = !support.hasBlockEntity() && !feet.hasBlockEntity() && !head.hasBlockEntity()
            && support.isFaceSturdy(reads, supportPos, net.minecraft.core.Direction.UP)
            && feet.getFluidState().isEmpty() && head.getFluidState().isEmpty()
            && !contactHazard.test(support) && !contactHazard.test(feet) && !contactHazard.test(head);
        final AABB collisionBox = moved.move(0, 1.0E-3, 0).deflate(1.0E-4);
        final net.minecraft.world.phys.shapes.CollisionContext context =
            net.minecraft.world.phys.shapes.CollisionContext.of(body);
        for (final BlockPos position : BlockPos.betweenClosed(
                BlockPos.containing(collisionBox.minX, collisionBox.minY, collisionBox.minZ),
                BlockPos.containing(collisionBox.maxX, collisionBox.maxY, collisionBox.maxZ))) {
            final BlockState state = reads.getBlockState(position);
            if (state.hasBlockEntity()) clear = false;
            for (final AABB shape : state.getCollisionShape(reads, position, context).toAabbs()) {
                if (shape.move(position).intersects(collisionBox)) clear = false;
            }
        }
        return new SafeObservation(clear && reads.withinContract(), reads.actualReads());
    }

    public static SafeObservation observeHazard(final ServerLevel level, final Entity body, final int readBudget,
            final Predicate<BlockState> contactHazard) {
        final BlockPos min = body.blockPosition().offset(-1, 0, -1);
        final BlockPos max = body.blockPosition().offset(1, 1, 1);
        final HaloReadCache reads = new HaloReadCache(level, min, max, readBudget);
        if (!reads.ready()) return new SafeObservation(false, 0);
        boolean hazard = false;
        for (final BlockPos position : BlockPos.betweenClosed(min, max)) {
            final BlockState state = reads.getBlockState(position);
            if (contactHazard.test(state) || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) hazard = true;
        }
        return new SafeObservation(hazard && reads.withinContract(), reads.actualReads());
    }

    static final class HaloReadCache implements BlockGetter {
        private final ServerLevel level;
        private final BlockPos min;
        private final BlockPos max;
        private final int budget;
        private final Map<BlockPos, BlockState> cache = new HashMap<>();
        private final boolean budgetProved;
        private int reads;
        private boolean rejected;

        HaloReadCache(final ServerLevel level, final AABB halo, final int budget) {
            this(level, BlockPos.containing(halo.minX, halo.minY, halo.minZ),
                BlockPos.containing(halo.maxX, halo.maxY, halo.maxZ), budget);
        }
        HaloReadCache(final ServerLevel level, final BlockPos min, final BlockPos max, final int budget) {
            this.level = level; this.min = min; this.max = max; this.budget = budget;
            final long volume = (long)(max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
            budgetProved = volume <= budget;
        }
        boolean ready() { return budgetProved && haloLoaded(); }
        boolean haloLoaded() {
            return level.hasChunkAt(min) && level.hasChunkAt(max)
                && level.hasChunkAt(new BlockPos(min.getX(), min.getY(), max.getZ()))
                && level.hasChunkAt(new BlockPos(max.getX(), max.getY(), min.getZ()));
        }
        boolean withinContract() { return !rejected; }
        int actualReads() { return reads; }
        @Override public BlockState getBlockState(final BlockPos position) {
            if (!ready() || position.getX() < min.getX() || position.getX() > max.getX()
                || position.getY() < min.getY() || position.getY() > max.getY()
                || position.getZ() < min.getZ() || position.getZ() > max.getZ()) {
                rejected = true; return Blocks.VOID_AIR.defaultBlockState();
            }
            final BlockPos key = position.immutable();
            final BlockState cached = cache.get(key);
            if (cached != null) return cached;
            if (reads >= budget) { rejected = true; return Blocks.VOID_AIR.defaultBlockState(); }
            reads++;
            final BlockState state = level.getBlockState(key);
            cache.put(key, state);
            return state;
        }
        @Override public FluidState getFluidState(final BlockPos position) { return getBlockState(position).getFluidState(); }
        @Override public BlockEntity getBlockEntity(final BlockPos position) { rejected = true; return null; }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }

    public static final class Quota {
        private int tick = Integer.MIN_VALUE;
        private int expensive, paths, entities, sights, reads, occupancy, wails, dreams, melee, bulbs, feedback;
        private boolean allowed = true;
        private static Quota denied() { var q = new Quota(); q.allowed = false; return q; }
        private void reset(final int current) {
            if (tick == current) return;
            tick = current; expensive = paths = entities = sights = reads = occupancy = 0;
            wails = dreams = melee = bulbs = feedback = 0;
        }
        private boolean take(final int amount, final int used, final int cap) { return allowed && amount >= 0 && used + amount <= cap; }
        public boolean expensive() { if (!take(1, expensive, 16)) return false; expensive++; return true; }
        public boolean path() { if (!take(1, paths, 8)) return false; paths++; return true; }
        public boolean entities(int n) { if (!take(n, entities, 128)) return false; entities += n; return true; }
        public boolean sight() { if (!take(1, sights, 32)) return false; sights++; return true; }
        public boolean reads(int n) { if (!take(n, reads, 512)) return false; reads += n; return true; }
        public boolean occupancy(int n) { if (!take(n, occupancy, 128)) return false; occupancy += n; return true; }
        public boolean wail() { if (!take(1, wails, 4)) return false; wails++; return true; }
        public boolean dream() { if (!take(1, dreams, 4)) return false; dreams++; return true; }
        public boolean melee() { if (!take(1, melee, 8)) return false; melee++; return true; }
        public boolean bulb() { if (!take(1, bulbs, 8)) return false; bulbs++; return true; }
        public boolean feedback() { if (!take(1, feedback, 8)) return false; feedback++; return true; }
    }
}
