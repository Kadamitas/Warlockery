package com.kadamitas.warlockery.item;

import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class DollBehaviorFactory {
    private DollBehaviorFactory() {
    }

    public static LethalDollBehavior earthGuard() {
        return configured(
            source -> source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_MACE_SMASH)
                || source.is(DamageTypes.FALLING_ANVIL)
                || source.is(DamageTypes.FALLING_BLOCK)
                || source.is(DamageTypes.FALLING_STALACTITE)
                || source.is(DamageTypes.STALAGMITE)
                || source.is(DamageTypes.FLY_INTO_WALL),
            (player, _) -> {
                player.resetFallDistance();
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
            }
        );
    }

    public static LethalDollBehavior waterGuard() {
        return configured(source -> source.is(DamageTypeTags.IS_DROWNING), (player, _) -> {
            player.setAirSupply(player.getMaxAirSupply());
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 600, 0));
        });
    }

    public static LethalDollBehavior hungerGuard() {
        return configured(source -> source.is(DamageTypes.STARVE), (player, _) -> {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(10.0F);
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20, 0));
        });
    }

    public static LethalDollBehavior fireGuard() {
        return configured(source -> source.is(DamageTypeTags.IS_FIRE), (player, source) -> {
            player.clearFire();
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0));
            if (source.is(DamageTypes.LAVA)) {
                rescueFromLava(player);
            }
        });
    }

    public static LethalDollBehavior deathGuard() {
        return configured(_ -> true, (_, _) -> { });
    }

    private static LethalDollBehavior configured(
        final Predicate<DamageSource> matcher,
        final BiConsumer<ServerPlayer, DamageSource> recovery
    ) {
        return new ConfiguredBehavior(matcher, recovery);
    }

    private static void rescueFromLava(final ServerPlayer player) {
        final ServerLevel level = (ServerLevel) player.level();
        final BlockPos origin = player.blockPosition();
        BlockPos.betweenClosedStream(origin.offset(-4, 0, -4), origin.offset(4, 10, 4))
            .filter(pos -> isSafeStandingSpot(level, pos))
            .min(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
            .ifPresent(pos -> {
                player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                player.resetFallDistance();
            });
    }

    private static boolean isSafeStandingSpot(final ServerLevel level, final BlockPos pos) {
        final BlockPos below = pos.below();
        return level.isEmptyBlock(pos)
            && level.isEmptyBlock(pos.above())
            && level.getFluidState(pos).isEmpty()
            && level.getFluidState(pos.above()).isEmpty()
            && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private record ConfiguredBehavior(
        Predicate<DamageSource> matcher,
        BiConsumer<ServerPlayer, DamageSource> recovery
    ) implements LethalDollBehavior {
        private ConfiguredBehavior {
            matcher = java.util.Objects.requireNonNull(matcher, "matcher");
            recovery = java.util.Objects.requireNonNull(recovery, "recovery");
        }

        @Override
        public boolean protectsAgainst(final DamageSource source) {
            return matcher.test(source);
        }

        @Override
        public void recover(final ServerPlayer player, final DamageSource source) {
            recovery.accept(player, source);
        }
    }
}
