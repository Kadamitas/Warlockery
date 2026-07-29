package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.brew.BrewArea;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PlantMineEffects {
    private static final int MAX_GROWABLES = 256;
    private static final int MAX_TERRAIN_BLOCKS = 24;
    private static final int MAX_THORNS = 20;
    private static final int MAX_WEBS = 24;
    private static final List<Direction> GROWTH_DIRECTIONS = List.of(
        Direction.UP,
        Direction.NORTH,
        Direction.SOUTH,
        Direction.EAST,
        Direction.WEST
    );

    private PlantMineEffects() {
    }

    static ActivationResult activate(
        final ServerLevel level,
        final BlockPos center,
        final PlantMinePayload payload
    ) {
        final ActivationResult result = switch (payload) {
            case INK -> ink(level, center, payload);
            case SPROUTING -> sprout(level, center, payload);
            case THORNS -> thorns(level, center, payload);
            case WEBS -> webs(level, center, payload);
            case UNARMED -> ActivationResult.ZERO;
        };
        showEffect(level, center, payload);
        return result;
    }

    private static ActivationResult ink(
        final ServerLevel level,
        final BlockPos center,
        final PlantMinePayload payload
    ) {
        final List<LivingEntity> targets = targets(level, center, payload.radius());
        targets.forEach(target -> target.addEffect(new MobEffectInstance(
            MobEffects.BLINDNESS,
            payload.duration(),
            0,
            true,
            true
        )));
        return new ActivationResult(targets.size(), 0);
    }

    private static ActivationResult sprout(
        final ServerLevel level,
        final BlockPos center,
        final PlantMinePayload payload
    ) {
        int grown = 0;
        final List<BlockPos> growables = BrewArea.sphere(center, payload.radius())
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.PLANT_MINE_GROWABLES))
            .limit(MAX_GROWABLES)
            .toList();
        for (int pass = 0; pass < 3; pass++) {
            for (BlockPos pos : growables) {
                final BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof BonemealableBlock growable)) {
                    continue;
                }
                if (PlantMineRules.canGrowVegetation(
                    state.is(WarlockeryTags.Blocks.PLANT_MINE_GROWABLES),
                    true,
                    growable.isValidBonemealTarget(level, pos, state),
                    growable.isBonemealSuccess(level, level.getRandom(), pos, state)
                )) {
                    growable.performBonemeal(level, level.getRandom(), pos, state);
                    grown++;
                }
            }
        }
        return new ActivationResult(0, grown + growTerrain(level, center, payload.radius()));
    }

    private static int growTerrain(final ServerLevel level, final BlockPos center, final int radius) {
        final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        final Set<BlockPos> visited = new HashSet<>();
        BrewArea.sphere(center, radius)
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.PLANT_MINE_GROWTH_GROUND))
            .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
            .limit(12)
            .map(BlockPos::immutable)
            .forEach(pos -> {
                pending.addLast(pos);
                visited.add(pos);
            });
        int changed = 0;
        while (!pending.isEmpty() && changed < MAX_TERRAIN_BLOCKS) {
            final BlockPos source = pending.removeFirst();
            for (Direction direction : GROWTH_DIRECTIONS) {
                final BlockPos target = source.relative(direction).immutable();
                if (!visited.add(target) || target.equals(center)) {
                    continue;
                }
                final boolean withinRadius = target.distSqr(center) <= (long) radius * radius;
                if (!PlantMineRules.canPlaceTerrain(
                    level.getBlockState(target).canBeReplaced(),
                    level.getFluidState(target).isEmpty(),
                    unoccupied(level, target),
                    withinRadius
                )) {
                    continue;
                }
                if (level.setBlockAndUpdate(target, Blocks.MOSS_BLOCK.defaultBlockState())) {
                    pending.addLast(target);
                    changed++;
                    if (changed >= MAX_TERRAIN_BLOCKS) {
                        break;
                    }
                }
            }
        }
        return changed;
    }

    private static ActivationResult thorns(
        final ServerLevel level,
        final BlockPos center,
        final PlantMinePayload payload
    ) {
        int changed = 0;
        final List<BlockPos> positions = BrewArea.sphere(center, payload.radius())
            .filter(pos -> !pos.equals(center))
            .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
            .toList();
        for (BlockPos pos : positions) {
            if (changed >= MAX_THORNS) {
                break;
            }
            final BlockPos groundPos = pos.below();
            final BlockState ground = level.getBlockState(groundPos);
            final BlockState cactus = Blocks.CACTUS.defaultBlockState();
            final BlockState berryBush = Blocks.SWEET_BERRY_BUSH.defaultBlockState()
                .setValue(SweetBerryBushBlock.AGE, SweetBerryBushBlock.MAX_AGE);
            final boolean cactusGround = ground.is(WarlockeryTags.Blocks.PLANT_MINE_CACTUS_GROUND);
            final BlockState placement = cactusGround ? cactus : berryBush;
            final boolean taggedGround = cactusGround
                || ground.is(WarlockeryTags.Blocks.PLANT_MINE_THORN_GROUND);
            if (PlantMineRules.canPlaceThorn(
                level.getBlockState(pos).canBeReplaced(),
                taggedGround,
                placement.canSurvive(level, pos),
                level.getFluidState(pos).isEmpty(),
                unoccupied(level, pos)
            ) && level.setBlockAndUpdate(pos, placement)) {
                changed++;
            }
        }
        return new ActivationResult(0, changed);
    }

    private static ActivationResult webs(
        final ServerLevel level,
        final BlockPos center,
        final PlantMinePayload payload
    ) {
        final List<LivingEntity> targets = targets(level, center, payload.radius());
        targets.forEach(target -> target.addEffect(new MobEffectInstance(
            MobEffects.SLOWNESS,
            payload.duration(),
            2,
            true,
            true
        )));
        final Set<BlockPos> positions = new HashSet<>();
        targets.stream().map(Entity::blockPosition).map(BlockPos::immutable).forEach(positions::add);
        BrewArea.sphere(center, payload.radius())
            .filter(pos -> !pos.equals(center))
            .filter(pos -> level.getBlockState(pos.below()).is(WarlockeryTags.Blocks.PLANT_MINE_WEB_SUPPORTS))
            .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
            .limit(MAX_WEBS)
            .map(BlockPos::immutable)
            .forEach(positions::add);
        int changed = 0;
        for (BlockPos pos : positions) {
            if (changed >= MAX_WEBS) {
                break;
            }
            if (PlantMineRules.canPlaceWeb(
                level.getBlockState(pos).canBeReplaced(),
                level.getFluidState(pos).isEmpty(),
                level.getBlockEntity(pos) == null
            ) && level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState())) {
                changed++;
            }
        }
        return new ActivationResult(targets.size(), changed);
    }

    private static List<LivingEntity> targets(final ServerLevel level, final BlockPos center, final int radius) {
        final AABB area = new AABB(center).inflate(radius, Math.max(2, radius / 2.0), radius);
        final double radiusSquared = (double) radius * radius;
        return level.getEntitiesOfClass(LivingEntity.class, area, entity ->
            PlantMineRules.canAffect(
                true,
                entity.isAlive(),
                entity.typeHolder().is(WarlockeryTags.EntityTypes.PLANT_MINE_IMMUNE),
                entity instanceof Player player && player.isSpectator()
            ) && entity.distanceToSqr(Vec3.atCenterOf(center)) <= radiusSquared
        );
    }

    private static boolean unoccupied(final ServerLevel level, final BlockPos pos) {
        return level.getEntities((Entity) null, new AABB(pos), Entity::isAlive).isEmpty();
    }

    private static void showEffect(
        final ServerLevel level,
        final BlockPos center,
        final PlantMinePayload payload
    ) {
        final var particle = switch (payload) {
            case INK -> ParticleTypes.SQUID_INK;
            case SPROUTING -> ParticleTypes.HAPPY_VILLAGER;
            case THORNS -> ParticleTypes.DAMAGE_INDICATOR;
            case WEBS -> ParticleTypes.ITEM_COBWEB;
            case UNARMED -> ParticleTypes.WITCH;
        };
        final var sound = switch (payload) {
            case INK -> SoundEvents.SPLASH_POTION_BREAK;
            case SPROUTING -> SoundEvents.BONE_MEAL_USE;
            case THORNS -> SoundEvents.SWEET_BERRY_BUSH_PLACE;
            case WEBS -> SoundEvents.SPIDER_STEP;
            case UNARMED -> SoundEvents.GRASS_PLACE;
        };
        level.sendParticles(particle, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
            36, 1.5, 0.75, 1.5, 0.05);
        level.playSound(null, center, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    record ActivationResult(int affectedEntities, int changedBlocks) {
        static final ActivationResult ZERO = new ActivationResult(0, 0);

        ActivationResult {
            if (affectedEntities < 0 || changedBlocks < 0) {
                throw new IllegalArgumentException("Activation counts cannot be negative");
            }
        }
    }
}
