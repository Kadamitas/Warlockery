package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;

public final class BrazierEffectRuntime {
    private static final int BUFF_RADIUS = 4;
    private static final int VEIL_RADIUS = 6;
    private static final int DRAIN_RADIUS = 3;
    private static final int BUFF_INTERVAL = 60;
    private static final int SUMMON_PARTICLE_INTERVAL = 100;
    private static final int DRAIN_INTERVAL = 5;
    private static final int SUSTAINED_DURATION = 120;
    private static final int MAX_SUMMON_ATTEMPTS = 8;
    private static final SpellParticleOption INSTANT_SPELL = SpellParticleOption.create(
        ParticleTypes.INSTANT_EFFECT,
        0xFFFFFF,
        1.0F
    );

    private BrazierEffectRuntime() {
    }

    public static Result applyDuringBurn(
        final ServerLevel level,
        final BlockPos center,
        final Identifier recipeId,
        final int previousProgress,
        final int currentProgress
    ) {
        if (currentProgress <= previousProgress) {
            return Result.NONE;
        }
        final long loadedTick = level.getGameTime();
        return Effect.fromRecipe(recipeId).map(effect -> switch (effect) {
            case SUMMON_SPECTRE, SUMMON_BANSHEE, SUMMON_POLTERGEIST ->
                BrazierEffectRules.shouldActivate(loadedTick, SUMMON_PARTICLE_INTERVAL)
                    ? instantSpellParticles(level, center)
                    : Result.NONE;
            case GRAVEYARD_MIST -> graveyardMist(level, center);
            case ANGUISH_OF_THE_DEAD -> BrazierEffectRules.shouldActivate(loadedTick, BUFF_INTERVAL)
                ? applyEffect(level, center, BUFF_RADIUS, MobEffects.STRENGTH, 0)
                : Result.NONE;
            case FORTIFICATION_OF_THE_CORPSE -> BrazierEffectRules.shouldActivate(loadedTick, BUFF_INTERVAL)
                ? applyEffect(level, center, BUFF_RADIUS, MobEffects.RESISTANCE, 0)
                : Result.NONE;
            case DEATHLY_VEIL -> BrazierEffectRules.shouldActivate(loadedTick, BUFF_INTERVAL)
                ? applyEffect(level, center, VEIL_RADIUS, MobEffects.INVISIBILITY, 0)
                : Result.NONE;
            case DRAIN_GROWTH -> BrazierEffectRules.shouldActivate(loadedTick, DRAIN_INTERVAL)
                ? drainGrowth(level, center, loadedTick)
                : Result.NONE;
        }).orElse(Result.NONE);
    }

    public static Result apply(final ServerLevel level, final BlockPos center, final Identifier recipeId) {
        return Effect.fromRecipe(recipeId).map(effect -> switch (effect) {
            case SUMMON_SPECTRE -> summon(level, center, "spectre");
            case SUMMON_BANSHEE -> summon(level, center, "banshee");
            case SUMMON_POLTERGEIST -> summon(level, center, "poltergeist");
            case GRAVEYARD_MIST, ANGUISH_OF_THE_DEAD, FORTIFICATION_OF_THE_CORPSE,
                DEATHLY_VEIL, DRAIN_GROWTH -> Result.NONE;
        }).orElse(Result.NONE);
    }

    private static Result summon(final ServerLevel level, final BlockPos center, final String entityId) {
        final int primary = spawn(level, center, entityId, 1, 2, true);
        if (primary == 0) {
            return Result.NONE;
        }
        final int bonus = level.getRandom().nextInt(20) == 0
            ? spawn(level, center, "poltergeist", 6, 10, false)
            : 0;
        return new Result(primary + bonus, 0, 0);
    }

    private static int spawn(
        final ServerLevel level,
        final BlockPos center,
        final String entityId,
        final int minimumRange,
        final int maximumRange,
        final boolean primary
    ) {
        final var registration = ModEntities.ALL.get(entityId);
        if (registration == null || !registration.isPresent()) {
            return 0;
        }
        final var created = registration.get().create(level, EntitySpawnReason.EVENT);
        if (!(created instanceof Mob summoned)) {
            return 0;
        }
        for (int attempt = 0; attempt < MAX_SUMMON_ATTEMPTS; attempt++) {
            final Optional<BlockPos> candidate = safeSummonCandidate(level, center, minimumRange, maximumRange);
            if (candidate.isEmpty()) {
                continue;
            }
            final BlockPos position = candidate.orElseThrow();
            summoned.snapTo(position.getX() + 0.5, position.getY() + 0.05, position.getZ() + 0.5);
            if (!level.noCollision(summoned)) {
                continue;
            }
            summoned.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(position),
                EntitySpawnReason.EVENT,
                null
            );
            summoned.setPersistenceRequired();
            if (!level.addFreshEntity(summoned)) {
                return 0;
            }
            summonFeedback(level, summoned, primary);
            return 1;
        }
        return 0;
    }

    private static Optional<BlockPos> safeSummonCandidate(
        final ServerLevel level,
        final BlockPos center,
        final int minimumRange,
        final int maximumRange
    ) {
        final int activeRadius = maximumRange - minimumRange;
        final int rollBound = activeRadius * 2 + 1;
        final int x = center.getX() + BrazierEffectRules.summonAxisOffset(
            level.getRandom().nextInt(rollBound),
            minimumRange,
            maximumRange
        );
        final int z = center.getZ() + BrazierEffectRules.summonAxisOffset(
            level.getRandom().nextInt(rollBound),
            minimumRange,
            maximumRange
        );
        final BlockPos column = new BlockPos(x, center.getY(), z);
        if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) {
            return Optional.empty();
        }

        int y = center.getY();
        final int searchTop = Math.min(level.getMaxY() - 2, center.getY() + 8);
        while (y < searchTop && !level.getBlockState(new BlockPos(x, y, z)).isAir()) {
            y++;
        }
        while (y > level.getMinY() && level.getBlockState(new BlockPos(x, y, z)).isAir()) {
            y--;
        }
        final BlockPos ground = new BlockPos(x, y, z);
        final BlockPos feet = ground.above();
        if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)
            || !level.getBlockState(feet).isAir()
            || !level.getBlockState(feet.above()).isAir()) {
            return Optional.empty();
        }
        return Optional.of(feet);
    }

    private static void summonFeedback(final ServerLevel level, final Mob summoned, final boolean primary) {
        level.sendParticles(
            primary ? INSTANT_SPELL : ParticleTypes.WITCH,
            summoned.getX(),
            summoned.getY(),
            summoned.getZ(),
            16,
            1.0,
            summoned.getBbHeight(),
            1.0,
            0.0
        );
        if (primary) {
            level.playSound(
                null,
                summoned.blockPosition(),
                SoundEvents.NOTE_BLOCK_HARP.value(),
                SoundSource.BLOCKS,
                1.0F,
                1.0F
            );
        }
    }

    private static Result instantSpellParticles(final ServerLevel level, final BlockPos center) {
        level.sendParticles(
            INSTANT_SPELL,
            center.getX() + 0.5,
            center.getY() + 1.0,
            center.getZ() + 0.5,
            16,
            0.5,
            1.0,
            0.5,
            0.0
        );
        return Result.NONE;
    }

    private static Result graveyardMist(final ServerLevel level, final BlockPos center) {
        level.sendParticles(
            ParticleTypes.EXPLOSION,
            center.getX() + 0.5,
            center.getY(),
            center.getZ() + 0.5,
            64,
            16.0,
            4.0,
            16.0,
            0.0
        );
        return Result.NONE;
    }

    private static Result applyEffect(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
        final int amplifier
    ) {
        final double centerX = center.getX() + 0.5;
        final double centerY = center.getY();
        final double centerZ = center.getZ() + 0.5;
        final double radiusSquared = (double) radius * radius;
        final var bounds = new AABB(
            centerX - radius,
            centerY - radius,
            centerZ - radius,
            centerX + radius,
            centerY + radius,
            centerZ + radius
        );
        final var targets = level.getEntitiesOfClass(LivingEntity.class, bounds, entity ->
            BrazierEffectRules.withinRadiusSquared(
                entity.getX() - centerX,
                entity.getY() - centerY,
                entity.getZ() - centerZ,
                radiusSquared
            )
        );
        targets.forEach(entity -> entity.addEffect(new MobEffectInstance(effect, SUSTAINED_DURATION, amplifier)));
        return new Result(0, targets.size(), 0);
    }

    private static Result drainGrowth(final ServerLevel level, final BlockPos center, final long activationTick) {
        final BlockPos cropPos = center.offset(
            level.getRandom().nextInt(DRAIN_RADIUS * 2 + 1) - DRAIN_RADIUS,
            -2 + BrazierEffectRules.drainGrowthOffsetY(activationTick),
            level.getRandom().nextInt(DRAIN_RADIUS * 2 + 1) - DRAIN_RADIUS
        );
        final var state = level.getBlockState(cropPos);
        if (!state.is(WarlockeryTags.Blocks.RITUAL_CROPS)
            || !(state.getBlock() instanceof CropBlock crop)
            || crop.getAge(state) <= 0) {
            return Result.NONE;
        }
        level.setBlockAndUpdate(cropPos, crop.getStateForAge(crop.getAge(state) - 1));

        level.sendParticles(
            ParticleTypes.WITCH,
            center.getX() + 0.5,
            center.getY() + 1.0,
            center.getZ() + 0.5,
            8,
            0.3,
            0.5,
            0.3,
            0.0
        );

        final var undead = level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(
                center.getX() - DRAIN_RADIUS,
                center.getY() - DRAIN_RADIUS,
                center.getZ() - DRAIN_RADIUS,
                center.getX() + DRAIN_RADIUS,
                center.getY() + DRAIN_RADIUS,
                center.getZ() + DRAIN_RADIUS
            ),
            entity -> BrazierEffectRules.canReceiveDrainGrowthHealing(
                entity.isAlive(),
                entity.typeHolder().is(EntityTypeTags.UNDEAD),
                entity.getHealth(),
                entity.getMaxHealth()
            )
        );
        undead.forEach(entity -> {
            entity.heal(entity.getMaxHealth() * 0.1F);
            level.sendParticles(
                ParticleTypes.HEART,
                entity.getX(),
                entity.getY() + entity.getBbHeight() * 0.6,
                entity.getZ(),
                4,
                0.3,
                0.3,
                0.3,
                0.0
            );
        });
        return new Result(0, undead.size(), 1);
    }

    public enum Effect {
        SUMMON_SPECTRE("brazier_summon_spectre"),
        SUMMON_BANSHEE("brazier_summon_banshee"),
        SUMMON_POLTERGEIST("brazier_summon_poltergeist"),
        GRAVEYARD_MIST("brazier_graveyard_mist"),
        ANGUISH_OF_THE_DEAD("brazier_anguish_of_the_dead"),
        FORTIFICATION_OF_THE_CORPSE("brazier_fortification_of_the_corpse"),
        DEATHLY_VEIL("brazier_deathly_veil"),
        DRAIN_GROWTH("brazier_drain_growth");

        private final String recipePath;

        Effect(final String recipePath) {
            this.recipePath = recipePath;
        }

        public String recipePath() {
            return recipePath;
        }

        public static Optional<Effect> fromRecipe(final Identifier recipeId) {
            return Arrays.stream(values()).filter(effect -> effect.recipePath.equals(recipeId.getPath())).findFirst();
        }
    }

    public record Result(int spawned, int affected, int cropsDrained) {
        public static final Result NONE = new Result(0, 0, 0);

        public Result {
            if (spawned < 0 || affected < 0 || cropsDrained < 0) {
                throw new IllegalArgumentException("Brazier effect results cannot be negative");
            }
        }

        public Result merge(final Result other) {
            return new Result(
                Math.addExact(spawned, other.spawned),
                Math.addExact(affected, other.affected),
                Math.addExact(cropsDrained, other.cropsDrained)
            );
        }

        public boolean changedWorld() {
            return spawned > 0 || affected > 0 || cropsDrained > 0;
        }
    }
}
