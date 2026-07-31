package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.entity.GoblinHostilityRules;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GoblinRaidRuntime {
    private GoblinRaidRuntime() {
    }

    public static void tick(final ServerLevel level) {
        final long gameTime = level.getGameTime();
        if (gameTime % GoblinRaidRules.CHECK_INTERVAL_TICKS != 0L) {
            return;
        }
        final GoblinRaidData data = GoblinRaidData.get(level);
        if (data.active().isPresent()) {
            tickActive(level, data, data.active().orElseThrow(), gameTime);
            return;
        }
        if (data.nextAttempt() == 0L) {
            data.scheduleNext(gameTime, level.getRandom().nextLong());
            return;
        }
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL || gameTime < data.nextAttempt()) {
            return;
        }
        final Optional<BlockPos> village = villageTarget(level);
        final boolean canStart = GoblinRaidRules.canStart(
            level.getDifficulty(),
            village.isPresent(),
            false,
            gameTime,
            data.nextAttempt()
        );
        data.scheduleNext(gameTime, level.getRandom().nextLong());
        if (canStart) {
            village.ifPresent(center -> data.begin(center, gameTime));
        }
    }

    public static void coordinate(final HobgoblinEntity goblin, final ServerLevel level) {
        final Optional<BlockPos> center = goblin.raidCenter();
        if (center.isEmpty() || !goblin.isVillageRaider() || goblin.isTrading() || goblin.isNoAi()) {
            return;
        }
        final LivingEntity current = goblin.getTarget();
        if (current != null && current.isAlive() && GoblinHostilityRules.isHumanVillager(current.getType())) {
            return;
        }
        final Optional<LivingEntity> sharedTarget = level.getEntitiesOfClass(
                HobgoblinEntity.class,
                goblin.getBoundingBox().inflate(32.0),
                other -> other != goblin
                    && other.isVillageRaider()
                    && other.raidCenter().filter(center.orElseThrow()::equals).isPresent()
                    && other.getTarget() != null
                    && other.getTarget().isAlive()
                    && GoblinHostilityRules.isHumanVillager(other.getTarget().getType())
            ).stream()
            .map(HobgoblinEntity::getTarget)
            .findFirst();
        final Optional<LivingEntity> target = sharedTarget.or(() -> level.getEntitiesOfClass(
                Villager.class,
                new AABB(center.orElseThrow()).inflate(48.0, 16.0, 48.0),
                villager -> villager.isAlive() && GoblinHostilityRules.isHumanVillager(villager.getType())
            ).stream()
            .min(Comparator.comparingDouble(goblin::distanceToSqr))
            .map(LivingEntity.class::cast));
        if (target.isPresent()) {
            goblin.setTarget(target.orElseThrow());
            return;
        }
        if (goblin.distanceToSqr(Vec3.atCenterOf(center.orElseThrow())) > 16.0) {
            final BlockPos destination = center.orElseThrow();
            goblin.getNavigation().moveTo(
                destination.getX() + 0.5,
                destination.getY(),
                destination.getZ() + 0.5,
                1.0
            );
        }
    }

    static int spawnWave(
        final ServerLevel level,
        final BlockPos center,
        final int wave,
        final int radius
    ) {
        final int size = GoblinRaidRules.waveSize(wave);
        final int direction = level.getRandom().nextInt(8);
        final BlockPos entry = entryPoint(level, center, radius, direction);
        final int stepX = Integer.signum(entry.getZ() - center.getZ());
        final int stepZ = -Integer.signum(entry.getX() - center.getX());
        int spawned = 0;
        for (int index = 0; index < size; index++) {
            final int offset = index - size / 2;
            final BlockPos position = surface(level, entry.offset(stepX * offset * 2, 0, stepZ * offset * 2));
            final HobgoblinEntity goblin = ModEntities.GOBLIN.get().spawn(
                level,
                position,
                EntitySpawnReason.PATROL
            );
            if (goblin != null) {
                goblin.joinVillageRaid(center, wave, spawned == 0);
                spawned++;
            }
        }
        return spawned;
    }

    private static void tickActive(
        final ServerLevel level,
        final GoblinRaidData data,
        final GoblinRaidData.RaidState state,
        final long gameTime
    ) {
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
            || gameTime >= state.expiresAt()
            || !level.isVillage(state.center())) {
            clearRaidMarkers(level, state.center());
            data.finish(gameTime, level.getRandom().nextLong());
            return;
        }
        final List<HobgoblinEntity> raiders = raiders(level, state.center());
        if (state.awaitingClear()) {
            if (raiders.isEmpty()) {
                data.update(state.waveCleared(gameTime));
            }
            return;
        }
        if (gameTime < state.nextWaveTime()) {
            return;
        }
        if (state.wave() >= GoblinRaidRules.WAVE_COUNT) {
            data.finish(gameTime, level.getRandom().nextLong());
            return;
        }
        final int wave = state.wave() + 1;
        final int spawned = spawnWave(level, state.center(), wave, GoblinRaidRules.SPAWN_RADIUS);
        data.update(spawned == 0 ? state.retryAt(gameTime) : state.waveSpawned(wave));
    }

    private static Optional<BlockPos> villageTarget(final ServerLevel level) {
        final List<ServerPlayer> candidates = level.players().stream()
            .filter(player -> !player.isSpectator())
            .filter(player -> level.isVillage(player.blockPosition()))
            .filter(player -> hasHumanVillagers(level, player.blockPosition()))
            .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        final BlockPos origin = candidates.get(level.getRandom().nextInt(candidates.size())).blockPosition();
        return SectionPos.cube(SectionPos.of(origin), 2)
            .filter(level::isVillage)
            .map(SectionPos::center)
            .min(Comparator.comparingDouble(origin::distSqr));
    }

    private static boolean hasHumanVillagers(final ServerLevel level, final BlockPos center) {
        return !level.getEntitiesOfClass(
            Villager.class,
            new AABB(center).inflate(48.0, 16.0, 48.0),
            villager -> GoblinHostilityRules.isHumanVillager(villager.getType())
        ).isEmpty();
    }

    private static List<HobgoblinEntity> raiders(final ServerLevel level, final BlockPos center) {
        return level.getEntitiesOfClass(
            HobgoblinEntity.class,
            new AABB(center).inflate(160.0, 64.0, 160.0),
            goblin -> goblin.isVillageRaider() && goblin.raidCenter().filter(center::equals).isPresent()
        );
    }

    private static void clearRaidMarkers(final ServerLevel level, final BlockPos center) {
        raiders(level, center).forEach(HobgoblinEntity::leaveVillageRaid);
    }

    private static BlockPos entryPoint(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final int initialDirection
    ) {
        BlockPos fallback = center;
        for (int attempt = 0; attempt < 8; attempt++) {
            final double angle = (initialDirection + attempt) * Math.PI / 4.0;
            final BlockPos candidate = surface(level, center.offset(
                (int) Math.round(Math.cos(angle) * radius),
                0,
                (int) Math.round(Math.sin(angle) * radius)
            ));
            fallback = candidate;
            if (level.isPositionEntityTicking(candidate) && !level.isVillage(candidate)) {
                return candidate;
            }
        }
        return fallback;
    }

    private static BlockPos surface(final ServerLevel level, final BlockPos position) {
        return new BlockPos(
            position.getX(),
            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position.getX(), position.getZ()),
            position.getZ()
        );
    }
}
