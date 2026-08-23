package com.kadamitas.warlockery.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

public final class ThornedPursuerRuntime {
    private static final String ESCORT_OWNER = "WarlockeryHuntEscort";
    private static final TagKey<net.minecraft.world.level.block.Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards"));
    private static final net.minecraft.resources.Identifier COURSE_MODIFIER =
        net.minecraft.resources.Identifier.fromNamespaceAndPath("warlockery", "thorned_pursuer_course");
    private static final WeakHashMap<Object, ThornedPursuerRules.LevelBudget> LEVEL_BUDGETS = new WeakHashMap<>();
    private static final ThreadLocal<Counters> ACTIVE_COUNTERS = new ThreadLocal<>();

    private ThornedPursuerRuntime() {}

    static void clearBudgetsForTest() { synchronized (LEVEL_BUDGETS) { LEVEL_BUDGETS.clear(); } }
    static BlockPos anchorForTest(ThornedPursuerEntity pursuer) {
        return pursuer.pursuerRuntime().anchor;
    }
    static UUID quarryIdForTest(ThornedPursuerEntity pursuer) { return pursuer.pursuerRuntime().quarry; }
    static boolean authorityClearedForTest(ThornedPursuerEntity pursuer) {
        Transient state = pursuer.pursuerRuntime();
        return state.quarry == null && state.quarryDimension == null && !state.escortEvaluated
            && !state.lastSight && state.retaliationLedger.size() == 0;
    }
    static boolean claimForTest(Object levelKey, int serverTick, boolean sameThread,
                                ThornedPursuerRules.Work work) {
        if (!sameThread) return false;
        synchronized (LEVEL_BUDGETS) {
            ThornedPursuerRules.LevelBudget current = LEVEL_BUDGETS.get(levelKey);
            if (current == null || current.serverTick() != serverTick) current = ThornedPursuerRules.LevelBudget.empty(serverTick);
            var next = current.take(work);
            if (next.isEmpty()) return false;
            LEVEL_BUDGETS.put(levelKey, next.orElseThrow());
            return true;
        }
    }

    private static boolean claim(ServerLevel level, ThornedPursuerRules.Work work) {
        boolean granted = claimForTest(level, level.getServer().getTickCount(), level.getServer().isSameThread(), work);
        Counters counters = ACTIVE_COUNTERS.get();
        if (counters != null) {
            if (granted) counters.tokensGranted++;
            else counters.tokensDeferred++;
        }
        return granted;
    }

    public static void tick(ThornedPursuerEntity pursuer, ServerLevel level) {
        ACTIVE_COUNTERS.set(pursuer.pursuerCounters());
        try {
            tickActive(pursuer, level);
        } finally {
            ACTIVE_COUNTERS.remove();
        }
    }

    private static void tickActive(ThornedPursuerEntity pursuer, ServerLevel level) {
        Transient state = pursuer.pursuerRuntime();
        Counters counters = pursuer.pursuerCounters();
        counters.aiTicks++; counters.cheapDecisions++;
        counters.stateKeys = 4; counters.stateBytes = 114;
        if (state.phase == ThornedPursuerRules.Phase.ANCHORED) counters.anchoredTicks++;
        if (state.anchor == null) {
            state.anchor = pursuer.blockPosition().immutable();
            String owner = pursuer.getPersistentData().getStringOr("WarlockerySummoningOwner", "");
            try { state.ownerHint = owner.isBlank() ? null : UUID.fromString(owner); }
            catch (IllegalArgumentException ignored) { state.ownerHint = null; }
            state.ownerHintRemaining = state.ownerHint == null ? 0 : ThornedPursuerRules.OWNER_HINT_TICKS;
        }
        state.phaseTicks++;
        state.retaliationLedger.tick();
        int previousHintRemaining = state.ownerHintRemaining;
        state.ownerHintRemaining = Math.max(0, state.ownerHintRemaining - 1);
        if (state.ownerHintRemaining == 0) {
            if (previousHintRemaining > 0 && state.ownerHint != null) counters.hintExpiries++;
            state.ownerHint = null;
        }
        state.episodeTicks += episode(state.phase) ? 1 : 0;
        boolean constantHazard = pursuer.isOnFire() || pursuer.isInLava();
        if (!constantHazard && ThornedPursuerRules.cadenceDue(pursuer.tickCount, pursuer.getId(), 20))
            state.contactHazardCached = observeHazard(pursuer, level) > 0;
        boolean observedHazard = state.contactHazardCached;
        if (constantHazard || observedHazard) {
            if (state.phase != ThornedPursuerRules.Phase.ESCAPE) {
                cancel(pursuer, level, ThornedPursuerRules.BreakReason.HAZARD);
                transition(pursuer, ThornedPursuerRules.Phase.ESCAPE);
                state.phaseTicks = 0;
            }
            escape(pursuer, level);
            return;
        }
        if (ThornedPursuerRules.episodeBudgetReached(state.phase, state.episodeTicks)) {
            breakEpisode(pursuer, level, ThornedPursuerRules.BreakReason.BUDGET);
            return;
        }
        switch (state.phase) {
            case ANCHORED -> anchored(pursuer, level);
            case BAY -> bay(pursuer, level);
            case COURSE -> course(pursuer, level);
            case SET -> set(pursuer, level);
            case PRESS -> press(pursuer, level);
            case BREAK -> breakEpisode(pursuer, level, ThornedPursuerRules.BreakReason.CANCELLED);
            case RECOVER -> recover(pursuer, level);
            case ESCAPE -> { transition(pursuer, ThornedPursuerRules.Phase.ANCHORED); state.anchor = pursuer.blockPosition().immutable(); counters.hazardEscapeSuccesses++; }
        }
    }

    private static int observeHazard(ThornedPursuerEntity pursuer, ServerLevel level) {
        BlockPos base = pursuer.blockPosition();
        int hazards = 0;
        int reads = 0;
        for (int y = 0; y <= 2 && reads < 18; y++) {
            for (int[] offset : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}}) {
                if (reads++ >= 18 || !claim(level, ThornedPursuerRules.Work.READ)) return hazards;
                var state = level.getBlockState(base.offset(offset[0], y, offset[1]));
                pursuer.pursuerCounters().hazardReads++; pursuer.pursuerCounters().hazardObservationReads++;
                if (hazard(state)) hazards++;
            }
        }
        return hazards;
    }

    private static boolean hazard(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(CONTACT_HAZARDS) || state.is(net.minecraft.world.level.block.Blocks.FIRE)
            || state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
            || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
    }

    private static void escape(ThornedPursuerEntity pursuer, ServerLevel level) {
        Transient runtime = pursuer.pursuerRuntime();
        if (runtime.routeBackoff > 0) { runtime.routeBackoff--; return; }
        if (!ThornedPursuerRules.cadenceDue(pursuer.tickCount, pursuer.getId(), 20)
            || !claim(level, ThornedPursuerRules.Work.EXPENSIVE)) return;
        int currentHazards = Math.max(1, observeHazard(pursuer, level));
        int totalEntityVisits = 0;
        int totalReads = 0;
        for (var offset : ThornedPursuerRules.safeOffsets()) {
            pursuer.pursuerCounters().safeCandidates++;
            BlockPos candidate = pursuer.blockPosition().offset(offset.x(), offset.y(), offset.z());
            AABB moved = pursuer.getBoundingBox().move(candidate.getX() + 0.5D - pursuer.getX(),
                candidate.getY() - pursuer.getY(), candidate.getZ() + 0.5D - pursuer.getZ());
            boolean loaded = level.hasChunkAt(BlockPos.containing(moved.minX - 1, moved.minY - 1, moved.minZ - 1))
                && level.hasChunkAt(BlockPos.containing(moved.maxX + 1, moved.maxY + 1, moved.maxZ + 1));
            if (!loaded || !level.getWorldBorder().isWithinBounds(candidate)) continue;
            int remainingReads = ThornedPursuerRules.MAX_SAFE_READS - totalReads;
            if (remainingReads <= 0) break;
            HaloReadCache blocks = new HaloReadCache(level, moved.inflate(1.0D), remainingReads,
                pursuer.pursuerCounters());
            if (!blocks.haloLoaded()) continue;
            var below = blocks.getBlockState(candidate.below());
            var feet = blocks.getBlockState(candidate);
            var head = blocks.getBlockState(candidate.above());
            boolean support = below.isFaceSturdy(blocks, candidate.below(), net.minecraft.core.Direction.UP);
            boolean collisionFree = feet.getCollisionShape(blocks, candidate).isEmpty()
                && head.getCollisionShape(blocks, candidate.above()).isEmpty();
            int candidateHazards = (hazard(feet) ? 1 : 0) + (hazard(head) ? 1 : 0);
            totalReads += blocks.actualReads();
            if (!blocks.withinContract()) break;
            final boolean[] occupied = {false};
            final int[] candidateVisits = {0};
            level.getEntities().get(EntityTypeTest.forClass(Entity.class), moved, entity -> {
                if (!claim(level, ThornedPursuerRules.Work.SAFE_ENTITY_VISIT)) return AbortableIterationConsumer.Continuation.ABORT;
                candidateVisits[0]++; pursuer.pursuerCounters().safeEntityVisits++;
                if (entity != pursuer) occupied[0] = true;
                return occupied[0] || candidateVisits[0] >= 8
                    ? AbortableIterationConsumer.Continuation.ABORT : AbortableIterationConsumer.Continuation.CONTINUE;
            });
            totalEntityVisits += candidateVisits[0];
            if (totalEntityVisits > 32) break;
            if (!ThornedPursuerRules.safeDestination(new ThornedPursuerRules.SafeFacts(
                true, true, collisionFree, support, !occupied[0], currentHazards, candidateHazards))) continue;
            if (path(pursuer, level, candidate)) { runtime.routeFailures = 0; pursuer.pursuerCounters().hazardRoutes++; return; }
        }
        runtime.routeFailures = ThornedPursuerRules.recordRouteFailure(runtime.routeFailures);
        if (ThornedPursuerRules.routeBackoffRequired(runtime.routeFailures)) {
            runtime.routeBackoff = ThornedPursuerRules.ROUTE_BACKOFF_TICKS;
            runtime.routeFailures = 0;
            pursuer.pursuerCounters().pathBackoffs++;
        }
    }

    /** Cache-backed collision view: no convenience collision query can hide uncharged level reads. */
    private static final class HaloReadCache implements net.minecraft.world.level.BlockGetter {
        private final ServerLevel level;
        private final BlockPos min;
        private final BlockPos max;
        private final int budget;
        private final Counters counters;
        private final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> cache = new java.util.HashMap<>();
        private int reads;
        private boolean rejected;

        private HaloReadCache(ServerLevel level, AABB halo, int budget, Counters counters) {
            this.level = level;
            this.min = BlockPos.containing(halo.minX, halo.minY, halo.minZ);
            this.max = BlockPos.containing(halo.maxX, halo.maxY, halo.maxZ);
            this.budget = budget;
            this.counters = counters;
        }

        private boolean haloLoaded() {
            return level.hasChunkAt(min) && level.hasChunkAt(max)
                && level.hasChunkAt(new BlockPos(min.getX(), min.getY(), max.getZ()))
                && level.hasChunkAt(new BlockPos(max.getX(), max.getY(), min.getZ()));
        }

        private boolean withinContract() { return !rejected; }
        private int actualReads() { return reads; }

        @Override
        public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos position) {
            if (position.getX() < min.getX() || position.getX() > max.getX()
                || position.getY() < min.getY() || position.getY() > max.getY()
                || position.getZ() < min.getZ() || position.getZ() > max.getZ()) {
                rejected = true;
                return net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
            }
            BlockPos key = position.immutable();
            var cached = cache.get(key);
            if (cached != null) return cached;
            if (reads >= budget || !claim(level, ThornedPursuerRules.Work.READ)) {
                rejected = true;
                return net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
            }
            reads++;
            if (counters != null) counters.safeReads++;
            var state = level.getBlockState(key);
            cache.put(key, state);
            return state;
        }

        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos position) {
            return getBlockState(position).getFluidState();
        }
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos position) {
            rejected = true;
            return null;
        }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }

    private static void anchored(ThornedPursuerEntity pursuer, ServerLevel level) {
        Transient state = pursuer.pursuerRuntime();
        pursuer.getNavigation().stop();
        pursuer.setTarget(null);
        if (!ThornedPursuerRules.cooldownDue(pursuer.pursuerState().episodeCooldownRemaining())
            || !ThornedPursuerRules.cadenceDue(pursuer.tickCount, pursuer.getId(), ThornedPursuerRules.QUARRY_SCAN_CADENCE)) return;
        if (!claim(level, ThornedPursuerRules.Work.EXPENSIVE)) return;
        pursuer.pursuerCounters().quarryScans++;
        List<LivingEntity> candidates = new ArrayList<>(ThornedPursuerRules.MAX_SCAN_VISITS);
        level.getEntities(EntityTypeTest.forClass(LivingEntity.class),
            pursuer.getBoundingBox().inflate(ThornedPursuerRules.QUARRY_SCAN_RADIUS),
            candidate -> true, candidates, ThornedPursuerRules.MAX_SCAN_VISITS);
        pursuer.pursuerCounters().scanVisits += candidates.size();
        pursuer.pursuerCounters().quarryRawVisits += candidates.size();
        candidates.stream().filter(candidate -> claim(level, ThornedPursuerRules.Work.ENTITY_VISIT))
            .filter(candidate -> legal(pursuer, candidate)
                && ThornedPursuerRules.withinQuarryScan(pursuer.distanceToSqr(candidate)))
            .sorted(java.util.Comparator.comparingDouble((LivingEntity candidate) -> pursuer.distanceToSqr(candidate))
                .thenComparing(candidate -> !candidate.getUUID().equals(state.ownerHint))
                .thenComparing(Entity::getUUID))
            .limit(ThornedPursuerRules.MAX_SCAN_SIGHT_RAYS)
            .filter(candidate -> { if (!claim(level, ThornedPursuerRules.Work.SIGHT_RAY)) return false; pursuer.pursuerCounters().sightRays++; pursuer.pursuerCounters().quarrySightRays++; return pursuer.getSensing().hasLineOfSight(candidate); })
            .findFirst().ifPresent(candidate -> {
                if (candidate.getUUID().equals(state.ownerHint)) pursuer.pursuerCounters().hintPreferences++;
                openEpisode(pursuer, level, candidate);
            });
    }

    static void openEpisode(ThornedPursuerEntity pursuer, ServerLevel level, LivingEntity quarry) {
        Transient state = pursuer.pursuerRuntime();
        state.quarry = quarry.getUUID(); state.quarryDimension = level.dimension();
        transition(pursuer, ThornedPursuerRules.Phase.BAY);
        state.phaseTicks = 0; state.episodeTicks = 0; state.escortEvaluated = true;
        pursuer.pursuerCounters().episodeStarts++; pursuer.pursuerCounters().quarryAcquisitions++;
        pursuer.pursuerCounters().bayStarts++;
        feedback(pursuer, level, net.minecraft.sounds.SoundEvents.RAVAGER_ROAR);
        pursuer.pursuerCounters().baySounds++;
        createEscorts(pursuer, level, quarry);
    }

    private static void bay(ThornedPursuerEntity pursuer, ServerLevel level) {
        pursuer.getNavigation().stop();
        QuarryResolution resolution = quarryResolution(pursuer, level);
        if (resolution.reason != null) { breakEpisode(pursuer, level, resolution.reason); return; }
        LivingEntity quarry = resolution.quarry;
        if (ThornedPursuerRules.bayElapsed(pursuer.pursuerRuntime().phaseTicks)) {
            enterCourse(pursuer);
            if (claim(level, ThornedPursuerRules.Work.SIGHT_RAY)) {
                pursuer.pursuerRuntime().lastSight = pursuer.getSensing().hasLineOfSight(quarry);
                pursuer.pursuerCounters().sightRays++;
            }
            path(pursuer, level, quarry.blockPosition());
        }
    }

    static void enterCourse(ThornedPursuerEntity pursuer) {
        Transient state = pursuer.pursuerRuntime(); transition(pursuer, ThornedPursuerRules.Phase.COURSE); state.phaseTicks = 0;
        pursuer.pursuerCounters().courseEntries++;
        var speed = pursuer.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(COURSE_MODIFIER) == null)
            { speed.addTransientModifier(new AttributeModifier(COURSE_MODIFIER, 0.03D, AttributeModifier.Operation.ADD_VALUE)); pursuer.pursuerCounters().courseModifierApplications++; }
    }
    static void enterSet(ThornedPursuerEntity pursuer) {
        removeCourse(pursuer);
        pursuer.getNavigation().stop();
        transition(pursuer, ThornedPursuerRules.Phase.SET);
        pursuer.pursuerRuntime().phaseTicks = 0;
        pursuer.pursuerCounters().holdTelegraphs++;
    }

    private static void course(ThornedPursuerEntity pursuer, ServerLevel level) {
        Transient state = pursuer.pursuerRuntime(); QuarryResolution resolution = quarryResolution(pursuer, level);
        if (resolution.reason != null) { breakEpisode(pursuer, level, resolution.reason); return; }
        LivingEntity quarry = resolution.quarry;
        boolean breakDue = ThornedPursuerRules.cadenceDue(
            pursuer.tickCount, pursuer.getId(), ThornedPursuerRules.BREAK_CADENCE);
        if (breakDue) {
            pursuer.pursuerCounters().breakEvaluations++;
            var reason = ThornedPursuerRules.scheduledBreakReason(new ThornedPursuerRules.BreakFacts(
                state.episodeTicks >= ThornedPursuerRules.EPISODE_BUDGET,
                !ThornedPursuerRules.withinLeash(pursuer.distanceToSqr(
                    state.anchor.getX() + 0.5D, state.anchor.getY(), state.anchor.getZ() + 0.5D)),
                !ThornedPursuerRules.withinRetention(pursuer.distanceToSqr(quarry)),
                !state.lastSight && state.trail.isEmpty(),
                ThornedPursuerRules.routeBackoffRequired(state.routeFailures)));
            if (reason.isPresent()) { breakEpisode(pursuer, level, reason.orElseThrow()); return; }
        }
        if (ThornedPursuerRules.cadenceDue(pursuer.tickCount, pursuer.getId(), ThornedPursuerRules.SIGHT_CADENCE)) {
            if (claim(level, ThornedPursuerRules.Work.SIGHT_RAY)) {
                state.lastSight = pursuer.getSensing().hasLineOfSight(quarry);
                pursuer.pursuerCounters().sightRays++;
            }
        }
        boolean sight = state.lastSight;
        if (!sight) pursuer.pursuerCounters().sightLossTicks++;
        if (sight && ThornedPursuerRules.cadenceDue(pursuer.tickCount, pursuer.getId(), ThornedPursuerRules.TRAIL_CADENCE)) {
            if (state.trail.size() == ThornedPursuerRules.TRAIL_CAPACITY) state.trail.removeFirst();
            state.trail.addLast(new TrailPoint(quarry.blockPosition().immutable(), 0));
            pursuer.pursuerCounters().trailWrites++;
        }
        ageTrail(pursuer, state);
        if (ThornedPursuerRules.mayEnterSet(pursuer.distanceToSqr(quarry), sight,
            pursuer.pursuerState().snareCooldownRemaining(), false)) {
            removeCourse(pursuer); pursuer.getNavigation().stop(); transition(pursuer, ThornedPursuerRules.Phase.SET); state.phaseTicks = 0; pursuer.pursuerCounters().holdTelegraphs++; return;
        }
        if (pursuer.distanceToSqr(quarry) <= ThornedPursuerRules.HOLD_DISTANCE_SQR && sight
            && !ThornedPursuerRules.cooldownDue(pursuer.pursuerState().snareCooldownRemaining()))
            pursuer.pursuerCounters().holdCooldownBlocks++;
        if (pursuer.isWithinMeleeAttackRange(quarry) && sight) { removeCourse(pursuer); transition(pursuer, ThornedPursuerRules.Phase.PRESS); state.phaseTicks = 0; return; }
        if (ThornedPursuerRules.cadenceDue(pursuer.tickCount, pursuer.getId(), ThornedPursuerRules.PATH_CADENCE)) {
            BlockPos destination = sight ? quarry.blockPosition() : state.trail.isEmpty() ? null : state.trail.getLast().position();
            if (!sight && destination != null) pursuer.pursuerCounters().trailFollowPaths++;
            if (destination == null || !path(pursuer, level, destination)) {
                if (recordRouteFailure(pursuer)) {
                    breakEpisode(pursuer, level, ThornedPursuerRules.BreakReason.ROUTE_FAILED);
                }
            } else state.routeFailures = 0;
        }
    }

    private static void set(ThornedPursuerEntity pursuer, ServerLevel level) {
        QuarryResolution resolution = quarryResolution(pursuer, level); Transient state = pursuer.pursuerRuntime();
        if (resolution.reason != null) { breakEpisode(pursuer, level, resolution.reason); return; }
        LivingEntity quarry = resolution.quarry;
        if (state.phaseTicks < ThornedPursuerRules.HOLD_TELEGRAPH_TICKS) return;
        boolean sight = claim(level, ThornedPursuerRules.Work.SIGHT_RAY)
            && pursuer.getSensing().hasLineOfSight(quarry);
        if (!ThornedPursuerRules.holdMayCommit(state.phaseTicks, legal(pursuer, quarry),
            sight, pursuer.distanceToSqr(quarry))) {
            pursuer.pursuerCounters().holdAbortsByReason[ThornedPursuerRules.BreakReason.CANCELLED.ordinal()]++;
            enterCourse(pursuer);
            return;
        }
        if (!claim(level, ThornedPursuerRules.Work.HOLD)) return;
        commitHold(pursuer, level, quarry);
    }
    static void commitHold(ThornedPursuerEntity pursuer, ServerLevel level, LivingEntity quarry) {
        Transient state = pursuer.pursuerRuntime();
        if (!quarry.hasEffect(MobEffects.SLOWNESS)) {
            quarry.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ThornedPursuerRules.HOLD_EFFECT_TICKS, 0));
            pursuer.pursuerCounters().slownessApplications++;
        }
        pursuer.setPursuerState(new ThornedPursuerState(1, ThornedPursuerRules.SNARE_COOLDOWN,
            pursuer.pursuerState().escortCooldownRemaining(), pursuer.pursuerState().episodeCooldownRemaining()));
        transition(pursuer, ThornedPursuerRules.Phase.PRESS); state.phaseTicks = 0; pursuer.pursuerCounters().holdCommits++;
        feedback(pursuer, level, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP);
    }
    private static void press(ThornedPursuerEntity pursuer, ServerLevel level) {
        QuarryResolution resolution = quarryResolution(pursuer, level); Transient state = pursuer.pursuerRuntime();
        if (resolution.reason != null) { breakEpisode(pursuer, level, resolution.reason); return; }
        LivingEntity quarry = resolution.quarry;
        if (ThornedPursuerRules.cadenceDue(pursuer.tickCount, pursuer.getId(), ThornedPursuerRules.BREAK_CADENCE)) {
            pursuer.pursuerCounters().breakEvaluations++;
            var reason = ThornedPursuerRules.scheduledBreakReason(new ThornedPursuerRules.BreakFacts(
                state.episodeTicks >= ThornedPursuerRules.EPISODE_BUDGET || state.phaseTicks >= ThornedPursuerRules.PRESS_TIMEOUT,
                !ThornedPursuerRules.withinLeash(pursuer.distanceToSqr(
                    state.anchor.getX() + 0.5D, state.anchor.getY(), state.anchor.getZ() + 0.5D)),
                !ThornedPursuerRules.withinRetention(pursuer.distanceToSqr(quarry)),
                false, ThornedPursuerRules.routeBackoffRequired(state.routeFailures)));
            if (reason.isPresent()) {
                if (state.phaseTicks >= ThornedPursuerRules.PRESS_TIMEOUT) pursuer.pursuerCounters().pressTimeouts++;
                breakEpisode(pursuer, level, reason.orElseThrow()); return;
            }
        }
        if (pursuer.isWithinMeleeAttackRange(quarry)
            && ThornedPursuerRules.cadenceDue(pursuer.tickCount, pursuer.getId(), ThornedPursuerRules.PRESS_CADENCE)
            && claim(level, ThornedPursuerRules.Work.SIGHT_RAY)
            && pursuer.getSensing().hasLineOfSight(quarry)) {
            if (!claim(level, ThornedPursuerRules.Work.MELEE)) return;
            boolean accepted = pursuer.doHurtTarget(level, quarry); pursuer.pursuerCounters().pressAttempts++;
            if (accepted) pursuer.pursuerCounters().pressAccepted++;
        }
        if (!pursuer.isWithinMeleeAttackRange(quarry)) enterCourse(pursuer);
    }

    private static void recover(ThornedPursuerEntity pursuer, ServerLevel level) {
        Transient state = pursuer.pursuerRuntime();
        if (ThornedPursuerRules.routeAttemptDeferred(state.routeBackoff)) {
            state.routeBackoff = ThornedPursuerRules.tickRouteBackoff(state.routeBackoff);
            stopMotion(pursuer);
            return;
        }
        if (ThornedPursuerRules.recoverComplete(pursuer.distanceToSqr(
            state.anchor.getX() + 0.5D, state.anchor.getY(), state.anchor.getZ() + 0.5D), state.routeFailures, state.phaseTicks)) {
            pursuer.pursuerCounters().recoverArrivals++; pursuer.pursuerCounters().reanchors++; state.reset(pursuer); return;
        }
        if (ThornedPursuerRules.cadenceDue(pursuer.tickCount, pursuer.getId(), ThornedPursuerRules.PATH_CADENCE)) {
            if (path(pursuer, level, state.anchor)) state.routeFailures = 0;
            else if (recordRouteFailure(pursuer)) stopMotion(pursuer);
        }
    }

    private static boolean recordRouteFailure(ThornedPursuerEntity pursuer) {
        Transient state = pursuer.pursuerRuntime();
        ThornedPursuerRules.RouteFailure next = ThornedPursuerRules.recordRouteFailure(
            state.routeFailures, state.routeBackoff);
        state.routeFailures = next.failures();
        state.routeBackoff = next.backoffTicks();
        if (state.routeBackoff > 0) pursuer.pursuerCounters().pathBackoffs++;
        return state.routeBackoff > 0;
    }

    public static void afterAcceptedDamage(ThornedPursuerEntity pursuer, ServerLevel level,
                                           LivingEntity attacker, float acceptedDamage) {
        afterAcceptedDamage(pursuer, level, attacker, acceptedDamage, 0);
    }

    static void afterAcceptedDamage(ThornedPursuerEntity pursuer, ServerLevel level,
                                    LivingEntity attacker, float acceptedDamage, int attributionAge) {
        ACTIVE_COUNTERS.set(pursuer.pursuerCounters());
        try {
            if (!ThornedPursuerRules.attributionFresh(attributionAge)) {
                pursuer.pursuerCounters().attackerExpiries++;
                pursuer.pursuerCounters().attackerRejectionsByReason[0]++;
                return;
            }
            afterAcceptedDamageActive(pursuer, level, attacker, acceptedDamage);
        } finally {
            ACTIVE_COUNTERS.remove();
        }
    }

    private static void afterAcceptedDamageActive(ThornedPursuerEntity pursuer, ServerLevel level,
                                                   LivingEntity attacker, float acceptedDamage) {
        if (!legal(pursuer, attacker)) { pursuer.pursuerCounters().attackerRejectionsByReason[1]++; return; }
        pursuer.pursuerCounters().attackerAttributions++;
        Transient state = pursuer.pursuerRuntime();
        UUID attackerId = attacker.getUUID();
        int nextStep = state.retaliationLedger.nextLadderStep(attackerId);
        boolean mayRetaliate = state.retaliationLedger.mayRetaliate(attackerId);
        float damage = ThornedPursuerRules.retaliationDamage(acceptedDamage, nextStep,
            pursuer.distanceToSqr(attacker), true, mayRetaliate ? 0 : 1);
        if (pursuer.distanceToSqr(attacker) > ThornedPursuerRules.HOLD_DISTANCE_SQR)
            pursuer.pursuerCounters().retaliationRangeRejections++;
        else if (!mayRetaliate) pursuer.pursuerCounters().retaliationCooldownBlocks++;
        if (damage > 0 && !state.retaliating && claim(level, ThornedPursuerRules.Work.RETALIATION)) {
            state.retaliating = true; attacker.hurtServer(level, level.damageSources().thorns(pursuer), damage); state.retaliating = false;
            state.retaliationLedger.recordRetaliation(attackerId);
            pursuer.pursuerCounters().retaliations++; pursuer.pursuerCounters().retaliationLadderSteps++;
        }
        if (!episode(state.phase)) openEpisode(pursuer, level, attacker);
        else if (!attackerId.equals(state.quarry)) replaceQuarry(pursuer, level, attacker);
    }

    private static void replaceQuarry(ThornedPursuerEntity pursuer, ServerLevel level, LivingEntity attacker) {
        Transient state = pursuer.pursuerRuntime();
        state.quarry = attacker.getUUID();
        state.quarryDimension = level.dimension();
        state.trail.clear();
        state.lastSight = false;
        pursuer.pursuerCounters().quarryAcquisitions++;
    }

    private static void createEscorts(ThornedPursuerEntity pursuer, ServerLevel level, LivingEntity quarry) {
        pursuer.pursuerCounters().escortEvaluations++;
        if (!ThornedPursuerRules.cooldownDue(pursuer.pursuerState().escortCooldownRemaining())) return;
        for (int i = 0; i < ThornedPursuerRules.MAX_ESCORTS; i++) {
            if (!claim(level, ThornedPursuerRules.Work.ESCORT)) break;
            Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.EVENT);
            if (wolf == null) continue;
            wolf.snapTo(pursuer.getX() + (i == 0 ? 1 : -1), pursuer.getY(), pursuer.getZ(), pursuer.getYRot(), 0);
            BlockPos position = wolf.blockPosition();
            HaloReadCache blocks = new HaloReadCache(level, wolf.getBoundingBox().inflate(1.0D), 16, null);
            boolean haloLoaded = blocks.haloLoaded();
            var floor = haloLoaded ? blocks.getBlockState(position.below()) : net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
            var feet = haloLoaded ? blocks.getBlockState(position) : net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
            var head = haloLoaded ? blocks.getBlockState(position.above()) : net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
            final boolean[] occupied = {false};
            final int[] visits = {0};
            level.getEntities().get(EntityTypeTest.forClass(Entity.class), wolf.getBoundingBox(), entity -> {
                if (!claim(level, ThornedPursuerRules.Work.SAFE_ENTITY_VISIT))
                    return AbortableIterationConsumer.Continuation.ABORT;
                visits[0]++;
                if (entity != pursuer && entity.canBeCollidedWith(wolf)) occupied[0] = true;
                return occupied[0] || visits[0] >= 8
                    ? AbortableIterationConsumer.Continuation.ABORT
                    : AbortableIterationConsumer.Continuation.CONTINUE;
            });
            if (!haloLoaded || !blocks.withinContract()
                || !level.getWorldBorder().isWithinBounds(position)
                || !floor.isFaceSturdy(blocks, position.below(), net.minecraft.core.Direction.UP)
                || !feet.getCollisionShape(blocks, position).isEmpty()
                || !head.getCollisionShape(blocks, position.above()).isEmpty() || occupied[0]) {
                pursuer.pursuerCounters().escortPositionRejections++; continue;
            }
            wolf.getPersistentData().putString(ESCORT_OWNER, pursuer.getUUID().toString());
            wolf.setTarget(quarry); level.addFreshEntity(wolf);
            pursuer.pursuerRuntime().escorts.add(wolf.getUUID()); pursuer.pursuerCounters().escortCreations++;
        }
        if (!pursuer.pursuerRuntime().escorts.isEmpty()) pursuer.setPursuerState(new ThornedPursuerState(1,
            pursuer.pursuerState().snareCooldownRemaining(), ThornedPursuerRules.ESCORT_COOLDOWN,
            pursuer.pursuerState().episodeCooldownRemaining()));
    }

    static void breakEpisode(ThornedPursuerEntity pursuer, ServerLevel level, ThornedPursuerRules.BreakReason reason) {
        Transient state = pursuer.pursuerRuntime(); releaseEscorts(pursuer, level); removeCourse(pursuer);
        if (state.quarry != null) pursuer.pursuerCounters().quarryReleasesByReason[reason.ordinal()]++;
        clearEpisodeAuthority(pursuer);
        if (reason != ThornedPursuerRules.BreakReason.ROUTE_FAILED) state.routeBackoff = 0;
        transition(pursuer, ThornedPursuerRules.Phase.RECOVER); state.phaseTicks = 0;
        pursuer.setPursuerState(new ThornedPursuerState(1, pursuer.pursuerState().snareCooldownRemaining(),
            pursuer.pursuerState().escortCooldownRemaining(), ThornedPursuerRules.EPISODE_COOLDOWN));
        pursuer.pursuerCounters().episodeCancels++;
        pursuer.pursuerCounters().episodeCancelsByReason[reason.ordinal()]++;
        pursuer.pursuerCounters().breaksByReason[reason.ordinal()]++;
        pursuer.pursuerCounters().recoverStarts++;
    }

    static FixtureFiveReport exerciseCancellationRecoverySeam(ThornedPursuerEntity pursuer, ServerLevel level) {
        ThornedPursuerRules.ReleaseFacts[] releaseFacts = {
            new ThornedPursuerRules.ReleaseFacts(true, true, false, false, true),
            new ThornedPursuerRules.ReleaseFacts(true, true, true, true, true),
            new ThornedPursuerRules.ReleaseFacts(true, true, true, false, false),
            new ThornedPursuerRules.ReleaseFacts(true, false, false, false, false),
            new ThornedPursuerRules.ReleaseFacts(false, false, false, false, false)
        };
        ThornedPursuerRules.BreakReason[] immediate = {
            ThornedPursuerRules.BreakReason.QUARRY_DEAD,
            ThornedPursuerRules.BreakReason.QUARRY_REMOVED,
            ThornedPursuerRules.BreakReason.QUARRY_ILLEGAL,
            ThornedPursuerRules.BreakReason.QUARRY_UNLOADED,
            ThornedPursuerRules.BreakReason.QUARRY_DIMENSION
        };
        boolean typedReleases = true;
        for (int index = 0; index < releaseFacts.length; index++) {
            var reason = ThornedPursuerRules.immediateReleaseReason(releaseFacts[index]).orElseThrow();
            typedReleases &= reason == immediate[index];
            pursuer.pursuerRuntime().quarry = pursuer.getUUID();
            breakEpisode(pursuer, level, reason);
        }
        ThornedPursuerRules.BreakFacts[] breakFacts = {
            new ThornedPursuerRules.BreakFacts(false, false, true, false, false),
            new ThornedPursuerRules.BreakFacts(false, false, false, true, false),
            new ThornedPursuerRules.BreakFacts(false, true, false, false, false),
            new ThornedPursuerRules.BreakFacts(true, false, false, false, false),
            new ThornedPursuerRules.BreakFacts(false, false, false, false, true)
        };
        ThornedPursuerRules.BreakReason[] scheduled = {
            ThornedPursuerRules.BreakReason.QUARRY_OUT_OF_RETENTION,
            ThornedPursuerRules.BreakReason.TRAIL_EXPIRED,
            ThornedPursuerRules.BreakReason.LEASH_EXCEEDED,
            ThornedPursuerRules.BreakReason.BUDGET,
            ThornedPursuerRules.BreakReason.ROUTE_FAILED
        };
        boolean typedScheduled = true;
        for (int index = 0; index < breakFacts.length; index++) {
            var reason = ThornedPursuerRules.scheduledBreakReason(breakFacts[index]).orElseThrow();
            typedScheduled &= reason == scheduled[index];
            pursuer.pursuerRuntime().quarry = pursuer.getUUID();
            breakEpisode(pursuer, level, reason);
        }
        Transient state = pursuer.pursuerRuntime();
        state.phase = ThornedPursuerRules.Phase.BAY;
        state.quarry = pursuer.getUUID();
        state.quarryDimension = level.dimension();
        state.episodeTicks = ThornedPursuerRules.EPISODE_BUDGET - 1;
        long budgetBreaks = pursuer.pursuerCounters().breaksByReason[ThornedPursuerRules.BreakReason.BUDGET.ordinal()];
        tick(pursuer, level);
        boolean bayBudget = state.phase == ThornedPursuerRules.Phase.RECOVER
            && pursuer.pursuerCounters().breaksByReason[ThornedPursuerRules.BreakReason.BUDGET.ordinal()] == budgetBreaks + 1;
        state.phase = ThornedPursuerRules.Phase.SET;
        state.quarry = pursuer.getUUID();
        state.quarryDimension = level.dimension();
        state.episodeTicks = ThornedPursuerRules.EPISODE_BUDGET - 1;
        clearBudgetsForTest();
        for (int hold = 0; hold < 4; hold++) claimForTest(level, level.getServer().getTickCount(), true,
            ThornedPursuerRules.Work.HOLD);
        tick(pursuer, level);
        boolean setBudget = state.phase == ThornedPursuerRules.Phase.RECOVER
            && pursuer.pursuerCounters().breaksByReason[ThornedPursuerRules.BreakReason.BUDGET.ordinal()] == budgetBreaks + 2;
        state.phase = ThornedPursuerRules.Phase.RECOVER;
        state.anchor = pursuer.blockPosition().offset(20, 0, 0);
        state.phaseTicks = ThornedPursuerRules.RECOVER_TIMEOUT;
        recover(pursuer, level);
        boolean timeout = state.phase == ThornedPursuerRules.Phase.ANCHORED;
        state.phase = ThornedPursuerRules.Phase.RECOVER;
        state.anchor = pursuer.blockPosition().offset(20, 0, 0);
        boolean firstTriggered = recordRouteFailure(pursuer);
        int firstFailures = state.routeFailures;
        boolean secondTriggered = recordRouteFailure(pursuer);
        int secondFailures = state.routeFailures;
        boolean thirdTriggered = recordRouteFailure(pursuer);
        int thirdBackoff = state.routeBackoff;
        if (thirdTriggered) breakEpisode(pursuer, level, ThornedPursuerRules.BreakReason.ROUTE_FAILED);
        long pathsBeforeBackoff = pursuer.pursuerCounters().pathRequests;
        for (int tick = 0; tick < ThornedPursuerRules.ROUTE_BACKOFF_TICKS; tick++) recover(pursuer, level);
        boolean backoff = state.phase == ThornedPursuerRules.Phase.RECOVER
            && state.routeBackoff == 0 && pursuer.pursuerCounters().pathRequests == pathsBeforeBackoff
            && authorityClearedForTest(pursuer);
        return new FixtureFiveReport(typedReleases, typedScheduled, timeout,
            !firstTriggered && firstFailures == 1 && !secondTriggered && secondFailures == 2
                && thirdTriggered, thirdBackoff >= 100, backoff, bayBudget && setBudget);
    }

    record FixtureFiveReport(boolean typedReleases, boolean typedScheduledBreaks,
                             boolean recoveryTimeout, boolean strictFailureSequence,
                             boolean boundedBackoff, boolean thirdFailureBackoff,
                             boolean bayAndSetBudgetArbitration) {}
    private static void cancel(ThornedPursuerEntity pursuer, ServerLevel level, ThornedPursuerRules.BreakReason reason) { breakEpisode(pursuer, level, reason); }
    private static void releaseEscorts(ThornedPursuerEntity pursuer, ServerLevel level) {
        for (UUID id : pursuer.pursuerRuntime().escorts) if (level.getEntity(id) instanceof Wolf wolf
            && pursuer.getUUID().toString().equals(wolf.getPersistentData().getStringOr(ESCORT_OWNER, ""))) {
            wolf.discard(); pursuer.pursuerCounters().escortReleases++;
        }
        pursuer.pursuerRuntime().escorts.clear();
    }
    static void onRemoved(ThornedPursuerEntity pursuer, ServerLevel level) {
        releaseEscorts(pursuer, level);
        pursuer.pursuerRuntime().reset(pursuer);
    }
    private static void clearEpisodeAuthority(ThornedPursuerEntity pursuer) {
        Transient state = pursuer.pursuerRuntime();
        state.quarry = null;
        state.quarryDimension = null;
        state.trail.clear();
        state.escorts.clear();
        state.escortEvaluated = false;
        state.lastSight = false;
        state.retaliationLedger.clear();
        state.retaliating = false;
        state.ownerHint = null;
        state.ownerHintRemaining = 0;
        state.episodeTicks = 0;
        state.routeFailures = 0;
        pursuer.setTarget(null);
        stopMotion(pursuer);
    }
    private static void stopMotion(ThornedPursuerEntity pursuer) {
        pursuer.getNavigation().stop();
        pursuer.getMoveControl().setWait();
        pursuer.setDeltaMovement(0.0D, pursuer.getDeltaMovement().y, 0.0D);
    }
    private static void removeCourse(ThornedPursuerEntity pursuer) { var speed = pursuer.getAttribute(Attributes.MOVEMENT_SPEED); if (speed != null && speed.getModifier(COURSE_MODIFIER) != null) { speed.removeModifier(COURSE_MODIFIER); pursuer.pursuerCounters().courseModifierRemovals++; } }
    private static boolean path(ThornedPursuerEntity pursuer, ServerLevel level, BlockPos destination) {
        if (!claim(level, ThornedPursuerRules.Work.PATH)) return false;
        PathNavigation navigation = pursuer.getNavigation(); var path = navigation.createPath(destination, 0);
        pursuer.pursuerCounters().pathRequests++;
        boolean accepted = path != null && path.canReach() && navigation.moveTo(path, 1.0D);
        if (accepted) pursuer.pursuerCounters().pathsAccepted++; else pursuer.pursuerCounters().pathFailures++;
        return accepted;
    }
    private static void feedback(ThornedPursuerEntity pursuer, ServerLevel level,
                                 net.minecraft.sounds.SoundEvent sound) {
        if (!claim(level, ThornedPursuerRules.Work.FEEDBACK)) {
            pursuer.pursuerCounters().feedbackSuppressed++; return;
        }
        pursuer.playSound(sound, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.2F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
            pursuer.getX(), pursuer.getY() + 1.0D, pursuer.getZ(), 8, 0.35D, 0.5D, 0.35D, 0.02D);
        pursuer.pursuerCounters().feedbackEmitted++; pursuer.pursuerCounters().sounds++;
        pursuer.pursuerCounters().particles += 8;
    }
    private static QuarryResolution quarryResolution(ThornedPursuerEntity pursuer, ServerLevel level) {
        Transient state = pursuer.pursuerRuntime();
        boolean sameDimension = state.quarryDimension == null || state.quarryDimension.equals(level.dimension());
        Entity resolved = sameDimension && state.quarry != null ? level.getEntity(state.quarry) : null;
        LivingEntity living = resolved instanceof LivingEntity candidate ? candidate : null;
        var reason = ThornedPursuerRules.immediateReleaseReason(new ThornedPursuerRules.ReleaseFacts(
            sameDimension, living != null, living != null && living.isAlive(),
            living != null && living.isRemoved(), living != null && legal(pursuer, living))).orElse(null);
        return new QuarryResolution(living, reason);
    }
    private record QuarryResolution(LivingEntity quarry, ThornedPursuerRules.BreakReason reason) {}
    private static boolean legal(ThornedPursuerEntity pursuer, LivingEntity candidate) {
        boolean protectedPlayer = candidate instanceof net.minecraft.world.entity.player.Player player
            && (player.isCreative() || player.isSpectator());
        boolean sleeping = candidate instanceof net.minecraft.world.entity.player.Player player && player.isSleeping();
        boolean trading = candidate instanceof net.minecraft.world.entity.npc.villager.Villager villager && villager.isTrading();
        boolean raid = candidate instanceof net.minecraft.world.entity.raid.Raider raider && raider.getCurrentRaid() != null;
        boolean breeding = candidate instanceof net.minecraft.world.entity.animal.Animal animal && animal.isInLove();
        boolean panic = candidate.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC);
        boolean ownedEscort = pursuer.getUUID().toString().equals(
            candidate.getPersistentData().getStringOr(ESCORT_OWNER, ""));
        return !candidate.isRemoved() && ThornedPursuerRules.eligibleQuarry(new ThornedPursuerRules.QuarryFacts(
            candidate.isAlive(), candidate == pursuer, candidate instanceof ThornedPursuerEntity,
            protectedPlayer, sleeping, trading, raid, panic, breeding,
            !ownedEscort && pursuer.canAttack(candidate)));
    }
    private static boolean episode(ThornedPursuerRules.Phase phase) { return phase == ThornedPursuerRules.Phase.BAY || phase == ThornedPursuerRules.Phase.COURSE || phase == ThornedPursuerRules.Phase.SET || phase == ThornedPursuerRules.Phase.PRESS; }
    private static void transition(ThornedPursuerEntity pursuer, ThornedPursuerRules.Phase next) {
        Transient state = pursuer.pursuerRuntime();
        if (state.phase != next) {
            int index = state.phase.ordinal() * ThornedPursuerRules.Phase.values().length + next.ordinal();
            pursuer.pursuerCounters().phaseTransitionsByPair[index]++;
            state.phase = next;
        }
    }
    private static void ageTrail(ThornedPursuerEntity pursuer, Transient state) {
        if (!state.trail.isEmpty()) {
            int before = state.trail.size();
            List<TrailPoint> kept = state.trail.stream().map(p -> new TrailPoint(p.position, p.age + 1))
                .filter(p -> !ThornedPursuerRules.trailExpired(p.age)).toList();
            state.trail.clear(); state.trail.addAll(kept);
            pursuer.pursuerCounters().trailExpiries += before - kept.size();
        }
    }

    static void seedTrailPoint(ThornedPursuerEntity pursuer, BlockPos position) {
        Transient state = pursuer.pursuerRuntime();
        if (state.trail.size() == ThornedPursuerRules.TRAIL_CAPACITY) state.trail.removeFirst();
        state.trail.addLast(new TrailPoint(position.immutable(), 0));
        pursuer.pursuerCounters().trailWrites++;
    }

    static void ageTrailLoadedTick(ThornedPursuerEntity pursuer) {
        ageTrail(pursuer, pursuer.pursuerRuntime());
    }
    static boolean courseModifierPresent(ThornedPursuerEntity pursuer) {
        var speed = pursuer.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(COURSE_MODIFIER) != null;
    }

    public record TrailPoint(BlockPos position, int age) {}
    public static final class Transient {
        private ThornedPursuerRules.Phase phase = ThornedPursuerRules.Phase.ANCHORED;
        private BlockPos anchor; private UUID quarry;
        private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> quarryDimension;
        private int phaseTicks; private int episodeTicks;
        private int routeFailures; private final Deque<TrailPoint> trail = new ArrayDeque<>();
        private int routeBackoff;
        private final List<UUID> escorts = new ArrayList<>(2); private boolean escortEvaluated;
        private boolean lastSight;
        private UUID ownerHint; private int ownerHintRemaining;
        private final ThornedPursuerRules.RetaliationLedger retaliationLedger =
            new ThornedPursuerRules.RetaliationLedger(8);
        private boolean retaliating;
        private boolean contactHazardCached;
        public ThornedPursuerRules.Phase phase() { return phase; }
        public List<UUID> escortIds() { return List.copyOf(escorts); }
        void resetForLoad(ThornedPursuerEntity pursuer) { reset(pursuer); anchor = null; }
        public void reset(ThornedPursuerEntity pursuer) { phase = ThornedPursuerRules.Phase.ANCHORED; anchor = pursuer.blockPosition().immutable(); phaseTicks = 0; routeBackoff = 0; contactHazardCached = false; clearEpisodeAuthority(pursuer); removeCourse(pursuer); }
    }
    public static final class Counters {
        public long aiTicks, cheapDecisions, anchoredTicks, episodeStarts;
        public long[] episodeCancelsByReason = new long[ThornedPursuerRules.BreakReason.values().length];
        public long[] phaseTransitionsByPair = new long[ThornedPursuerRules.Phase.values().length * ThornedPursuerRules.Phase.values().length];
        public long quarryScans, quarryRawVisits, quarrySightRays, quarryAcquisitions;
        public long[] quarryReleasesByReason = new long[ThornedPursuerRules.BreakReason.values().length];
        public long hintPreferences, hintExpiries, bayStarts, bayNavigationWrites, baySounds;
        public long courseEntries, courseModifierApplications, courseModifierRemovals, trailWrites,
            trailExpiries, trailFollowPaths, sightRays, sightLossTicks;
        public long holdTelegraphs, holdCommits;
        public long[] holdAbortsByReason = new long[ThornedPursuerRules.BreakReason.values().length];
        public long slownessApplications, holdCooldownBlocks, pressAttempts, pressAccepted, pressTimeouts,
            breakEvaluations;
        public long[] breaksByReason = new long[ThornedPursuerRules.BreakReason.values().length];
        public long recoverStarts, recoverArrivals, reanchors, attackerAttributions;
        public long[] attackerRejectionsByReason = new long[8];
        public long attackerExpiries, retaliations, retaliationLadderSteps, retaliationRangeRejections,
            retaliationCooldownBlocks, escortEvaluations, escortCreations, escortPositionRejections,
            escortReleases, escortOrphans, wolfScans, pathRequests, pathsAccepted, pathFailures,
            pathBackoffs, navigationOverwrites, hazardObservationReads, safeCandidates, safeReads,
            safeEntityVisits, hazardRoutes, hazardEscapeSuccesses, tokensGranted, tokensDeferred,
            feedbackEmitted, feedbackSuppressed, sounds, particles, genericBehaviorDispatches,
            genericTacticalDispatches, genericAmbientDispatches, genericHazardDispatches, teleports,
            projectileCreations, blockEdits, chunkLoadRequests, crossDimensionLookups, reinforcements,
            villagerConversions, drownedConversions, turtleEggBreaks, doorBreaks, babyStates,
            equipmentStates, piglinAlerts, stateKeys, stateBytes, stateMismatches, transientReplays;
        public long scanVisits, episodeCancels, hazardReads;
    }
}
