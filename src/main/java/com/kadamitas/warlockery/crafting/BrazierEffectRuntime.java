package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;

public final class BrazierEffectRuntime {
    private static final int RADIUS = 12;
    private static final int DURATION = 1_200;

    private BrazierEffectRuntime() {
    }

    public static Result apply(final ServerLevel level, final BlockPos center, final Identifier recipeId) {
        return Effect.fromRecipe(recipeId).map(effect -> switch (effect) {
            case SUMMON_SPECTRE -> summon(level, center, "spectre");
            case SUMMON_BANSHEE -> summon(level, center, "banshee");
            case GRAVEYARD_MIST -> graveyardMist(level, center);
            case ANGUISH_OF_THE_DEAD -> applyEffect(level, center, MobEffects.STRENGTH, 1);
            case FORTIFICATION_OF_THE_CORPSE -> applyEffect(level, center, MobEffects.RESISTANCE, 1);
            case DEATHLY_VEIL -> applyEffect(level, center, MobEffects.INVISIBILITY, 0);
            case DRAIN_GROWTH -> drainGrowth(level, center);
        }).orElse(Result.NONE);
    }

    private static Result summon(final ServerLevel level, final BlockPos center, final String entityId) {
        final var created = ModEntities.ALL.get(entityId).get().create(level, EntitySpawnReason.EVENT);
        if (!(created instanceof Mob summoned)) {
            return Result.NONE;
        }
        summoned.snapTo(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5);
        summoned.setPersistenceRequired();
        final int spawned = level.addFreshEntity(summoned) ? 1 : 0;
        if (spawned > 0 && level.getRandom().nextInt(10) == 0) {
            final var createdPoltergeist = ModEntities.ALL.get("poltergeist").get().create(level, EntitySpawnReason.EVENT);
            if (createdPoltergeist instanceof Mob poltergeist) {
                poltergeist.snapTo(center.getX() + 1.5, center.getY() + 1.0, center.getZ() + 0.5);
                poltergeist.setPersistenceRequired();
                level.addFreshEntity(poltergeist);
            }
        }
        return new Result(spawned, 0, 0);
    }

    private static Result graveyardMist(final ServerLevel level, final BlockPos center) {
        final var targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(16.0));
        targets.forEach(entity -> {
            if (entity.typeHolder().is(EntityTypeTags.UNDEAD)) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION, 0));
                entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DURATION, 0));
            } else {
                entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DURATION, 0));
                entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DURATION, 0));
            }
        });
        level.sendParticles(
            ParticleTypes.ASH,
            center.getX() + 0.5,
            center.getY() + 1.0,
            center.getZ() + 0.5,
            320,
            8.0,
            2.0,
            8.0,
            0.02
        );
        return new Result(0, targets.size(), 0);
    }

    private static Result applyEffect(
        final ServerLevel level,
        final BlockPos center,
        final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
        final int amplifier
    ) {
        final var targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(RADIUS));
        targets.forEach(entity -> entity.addEffect(new MobEffectInstance(effect, DURATION, amplifier)));
        return new Result(0, targets.size(), 0);
    }

    private static Result drainGrowth(final ServerLevel level, final BlockPos center) {
        final int drained = (int) BlockPos.betweenClosedStream(
                center.offset(-RADIUS, -2, -RADIUS),
                center.offset(RADIUS, 3, RADIUS)
            )
            .filter(position -> level.getBlockState(position).is(WarlockeryTags.Blocks.RITUAL_CROPS))
            .filter(position -> level.getBlockState(position).getBlock() instanceof CropBlock)
            .filter(position -> {
                final var state = level.getBlockState(position);
                return ((CropBlock) state.getBlock()).getAge(state) > 0;
            })
            .limit(256)
            .mapToInt(position -> {
                final CropBlock crop = (CropBlock) level.getBlockState(position).getBlock();
                level.setBlockAndUpdate(position, crop.getStateForAge(0));
                return 1;
            })
            .sum();
        final var undead = level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center).inflate(RADIUS),
            entity -> entity.typeHolder().is(EntityTypeTags.UNDEAD)
        );
        undead.forEach(entity -> entity.heal(Math.min(20.0F, drained * 0.5F)));
        return new Result(0, undead.size(), drained);
    }

    public enum Effect {
        SUMMON_SPECTRE("brazier_summon_spectre"),
        SUMMON_BANSHEE("brazier_summon_banshee"),
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

        public boolean changedWorld() {
            return spawned > 0 || affected > 0 || cropsDrained > 0;
        }
    }
}
