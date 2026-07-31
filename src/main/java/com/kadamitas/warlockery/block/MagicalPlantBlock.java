package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.MagicalPlantBlockFactory.Behavior;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class MagicalPlantBlock extends BushBlock {
    static final int TELEPORT_ATTEMPTS = 32;
    static final int TELEPORT_RADIUS = 500;
    private final Behavior behavior;

    MagicalPlantBlock(final Behavior behavior, final BlockBehaviour.Properties properties) {
        super(properties);
        this.behavior = behavior;
    }

    public Behavior behavior() {
        return behavior;
    }

    @Override
    protected boolean mayPlaceOn(final BlockState state, final BlockGetter level, final BlockPos pos) {
        return switch (behavior) {
            case EMBER_MOSS -> state.is(WarlockeryTags.Blocks.EMBER_MOSS_SPREADABLE_GROUND);
            case GLINT_WEED -> state.is(WarlockeryTags.Blocks.GLINT_WEED_SPREADABLE_GROUND);
            case ENDER_BRAMBLE -> state.is(WarlockeryTags.Blocks.ENDER_BRAMBLE_TELEPORT_GROUND);
            default -> super.mayPlaceOn(state, level, pos);
        };
    }

    @Override
    protected void randomTick(
        final BlockState state,
        final ServerLevel level,
        final BlockPos pos,
        final RandomSource random
    ) {
        if (!behavior.spreads() || random.nextInt(behavior.spreadChance()) != 0) {
            return;
        }
        trySpread(level, pos, random);
    }

    @Override
    public boolean isValidBonemealTarget(final LevelReader level, final BlockPos pos, final BlockState state) {
        if (!behavior.spreads()) {
            return false;
        }
        final int nearbyPlants = nearbyGlintWeed(level, pos);
        return BlockPos.betweenClosedStream(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))
            .anyMatch(target -> canSpreadAt(level, target, nearbyPlants));
    }

    @Override
    public boolean isBonemealSuccess(
        final Level level,
        final RandomSource random,
        final BlockPos pos,
        final BlockState state
    ) {
        return behavior.spreads();
    }

    @Override
    public void performBonemeal(
        final ServerLevel level,
        final RandomSource random,
        final BlockPos pos,
        final BlockState state
    ) {
        for (int attempt = 0; attempt < 8; attempt++) {
            if (trySpread(level, pos, random)) {
                return;
            }
        }
    }

    private boolean trySpread(final ServerLevel level, final BlockPos pos, final RandomSource random) {
        final int nearbyPlants = nearbyGlintWeed(level, pos);
        final BlockPos target = pos.offset(random.nextInt(5) - 2, random.nextInt(3) - 1,
            random.nextInt(5) - 2);
        if (!canSpreadAt(level, target, nearbyPlants)) {
            return false;
        }
        level.setBlockAndUpdate(target, defaultBlockState());
        return true;
    }

    private boolean canSpreadAt(final LevelReader level, final BlockPos target, final int nearbyPlants) {
        final BlockPos groundPos = target.below();
        final BlockState targetState = level.getBlockState(target);
        final BlockState groundState = level.getBlockState(groundPos);
        final boolean targetAir = targetState.isAir();
        final boolean stableGround = groundState.isFaceSturdy(level, groundPos, Direction.UP);
        final boolean dry = level.getFluidState(target).isEmpty();
        if (behavior == Behavior.GLINT_WEED) {
            return MagicalPlantRules.canSpreadGlintWeed(
                targetAir,
                groundState.is(WarlockeryTags.Blocks.GLINT_WEED_SPREADABLE_GROUND),
                stableGround,
                dry,
                level.getMaxLocalRawBrightness(target),
                nearbyPlants
            );
        }
        if (behavior == Behavior.ENDER_BRAMBLE) {
            return MagicalPlantRules.canSpreadBramble(
                targetAir,
                groundState.is(WarlockeryTags.Blocks.ENDER_BRAMBLE_TELEPORT_GROUND),
                stableGround,
                dry,
                nearbyPlants
            );
        }
        return MagicalPlantRules.canSpreadEmberMoss(
                targetAir,
                groundState.is(WarlockeryTags.Blocks.EMBER_MOSS_SPREADABLE_GROUND),
                stableGround,
                dry,
                nearbyPlants
            );
    }

    private int nearbyGlintWeed(final LevelReader level, final BlockPos pos) {
        return (int) BlockPos.betweenClosedStream(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))
            .filter(candidate -> level.getBlockState(candidate).getBlock() == this)
            .limit(MagicalPlantRules.MAX_NEARBY_SPREADERS)
            .count();
    }

    @Override
    protected void entityInside(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Entity entity,
        final InsideBlockEffectApplier effectApplier,
        final boolean isPrecise
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        switch (behavior) {
            case EMBER_MOSS -> ignite(entity);
            case LEAPING_LILY -> boost(entity);
            case ENDER_BRAMBLE -> thornAndTeleport(serverLevel, pos, entity);
            case GLINT_WEED, BLOOD_POPPY, CRITTER_SNARE, GRASSPER, SPANISH_MOSS -> {
            }
        }
    }

    private static void thornAndTeleport(final ServerLevel level, final BlockPos pos, final Entity entity) {
        if (entity instanceof LivingEntity living
            && !entity.typeHolder().is(WarlockeryTags.EntityTypes.ENDER_BRAMBLE_IMMUNE)) {
            living.hurtServer(level, living.damageSources().cactus(), 2.0F);
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
        }
        teleport(level, pos, entity);
    }

    private static void ignite(final Entity entity) {
        if (MagicalPlantRules.shouldIgnite(
            entity instanceof LivingEntity,
            entity.isShiftKeyDown(),
            entity.typeHolder().is(WarlockeryTags.EntityTypes.EMBER_MOSS_IMMUNE),
            entity.fireImmune()
        )) {
            entity.igniteForSeconds(4.0F);
        }
    }

    private static void boost(final Entity entity) {
        if (entity instanceof LivingEntity living && MagicalPlantRules.shouldBoost(true, living.tickCount)) {
            living.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, true));
            living.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 1, true, true));
        }
    }

    private static void teleport(final ServerLevel level, final BlockPos source, final Entity entity) {
        if (entity.isOnPortalCooldown()
            || entity.typeHolder().is(WarlockeryTags.EntityTypes.ENDER_BRAMBLE_IMMUNE)
            || entity.isPassenger()) {
            return;
        }
        final RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            final int x = source.getX() + random.nextInt(TELEPORT_RADIUS * 2 + 1) - TELEPORT_RADIUS;
            final int z = source.getZ() + random.nextInt(TELEPORT_RADIUS * 2 + 1) - TELEPORT_RADIUS;
            final BlockPos column = new BlockPos(x, source.getY(), z);
            if (!level.isLoaded(column)) {
                continue;
            }
            final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            final BlockPos target = new BlockPos(x, y, z);
            if (tryTeleport(level, source, target, entity, random)) {
                return;
            }
        }
    }

    private static boolean tryTeleport(
        final ServerLevel level,
        final BlockPos source,
        final BlockPos target,
        final Entity entity,
        final RandomSource random
    ) {
        if (!level.isInWorldBounds(target) || !level.isLoaded(target)) {
            return false;
        }
        final BlockPos groundPos = target.below();
        final BlockPos headPos = target.above();
        final BlockState ground = level.getBlockState(groundPos);
        final BlockState feet = level.getBlockState(target);
        final BlockState head = level.getBlockState(headPos);
        final boolean safe = MagicalPlantRules.canTeleport(
            false,
            false,
            ground.is(WarlockeryTags.Blocks.ENDER_BRAMBLE_TELEPORT_GROUND),
            ground.isFaceSturdy(level, groundPos, Direction.UP),
            feet.getCollisionShape(level, target).isEmpty(),
            head.getCollisionShape(level, headPos).isEmpty(),
            level.noCollision(entity, entity.getBoundingBox().move(
                target.getX() + 0.5 - entity.getX(),
                target.getY() - entity.getY(),
                target.getZ() + 0.5 - entity.getZ()
            )),
            level.getFluidState(target).isEmpty() && level.getFluidState(headPos).isEmpty()
        );
        if (!safe) {
            return false;
        }
        level.playSound(null, source, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.8F, 0.9F + random.nextFloat() * 0.2F);
        entity.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        entity.setPortalCooldown(40);
        level.playSound(null, target, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.8F, 0.9F + random.nextFloat() * 0.2F);
        return true;
    }
}
