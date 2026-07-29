package com.kadamitas.warlockery.ritual.hex;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class BlightHex {
    public static final java.util.List<Holder<MobEffect>> UI_EFFECTS = java.util.List.of(
        MobEffects.POISON,
        MobEffects.WEAKNESS
    );

    private BlightHex() {
    }

    public static BlightReport apply(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final int durationTicks
    ) {
        final int safeRadius = Math.clamp(radius, 1, 24);
        final AtomicInteger vegetation = new AtomicInteger();
        final AtomicInteger soils = new AtomicInteger();
        BlockPos.betweenClosedStream(
                center.offset(-safeRadius, -2, -safeRadius),
                center.offset(safeRadius, 3, safeRadius)
            )
            .filter(pos -> pos.distSqr(center) <= safeRadius * safeRadius)
            .limit(4_096)
            .forEach(pos -> {
                final var state = level.getBlockState(pos);
                if (state.is(WarlockeryTags.Blocks.BLIGHT_VEGETATION)
                    && level.getBlockEntity(pos) == null
                    && level.destroyBlock(pos, false)) {
                    vegetation.incrementAndGet();
                } else if (state.is(WarlockeryTags.Blocks.BLIGHT_SOILS)
                    && level.getBlockEntity(pos) == null
                    && level.setBlockAndUpdate(pos, Blocks.COARSE_DIRT.defaultBlockState())) {
                    soils.incrementAndGet();
                }
            });
        final var victims = level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center).inflate(safeRadius),
            entity -> entity.typeHolder().is(WarlockeryTags.EntityTypes.BLIGHT_VICTIMS)
        );
        victims.forEach(entity -> {
            entity.addEffect(new MobEffectInstance(MobEffects.POISON, Math.max(200, durationTicks / 2), 1));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Math.max(400, durationTicks), 1));
        });
        return new BlightReport(vegetation.get(), soils.get(), victims.size());
    }

    public record BlightReport(int vegetationDestroyed, int soilsSpoiled, int victimsAfflicted) {
        public BlightReport {
            if (vegetationDestroyed < 0 || soilsSpoiled < 0 || victimsAfflicted < 0) {
                throw new IllegalArgumentException("Blight report counts must be nonnegative");
            }
        }

        public int totalChanges() {
            return vegetationDestroyed + soilsSpoiled + victimsAfflicted;
        }

        public boolean changedAnything() {
            return totalChanges() > 0;
        }
    }
}
