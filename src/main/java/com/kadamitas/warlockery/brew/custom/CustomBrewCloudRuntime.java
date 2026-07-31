package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.brew.BrewMarkerKind;
import com.kadamitas.warlockery.brew.BrewMarkerState;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;

public final class CustomBrewCloudRuntime {
    private static final int MAX_NEARBY_CLOUDS = 32;

    private CustomBrewCloudRuntime() {
    }

    public static void tick(final ServerLevel level, final LivingEntity target) {
        if (!target.isAlive()) {
            return;
        }
        final List<AreaEffectCloud> clouds = level.getEntitiesOfClass(
            AreaEffectCloud.class,
            target.getBoundingBox().inflate(0.001),
            cloud -> cloud.isAlive() && !cloud.isWaiting() && CustomBrewCloudRules.delivery(cloud)
                .filter(CustomBrewDelivery::lingering)
                .isPresent()
        ).stream().limit(MAX_NEARBY_CLOUDS).toList();
        for (AreaEffectCloud cloud : clouds) {
            apply(level, cloud, target);
        }
    }

    private static void apply(
        final ServerLevel level,
        final AreaEffectCloud cloud,
        final LivingEntity target
    ) {
        final var formula = CustomBrewRuntime.read(cloud).orElse(null);
        if (formula == null || formula.skipEntities() || !inside(cloud, target)) {
            return;
        }
        final CustomBrewDelivery delivery = CustomBrewCloudRules.delivery(cloud).orElse(formula.delivery());
        if (CustomBrewCloudRules.blocksDelivery(
            delivery,
            BrewMarkerState.isActive(target, BrewMarkerKind.BREW_GAS_IMMUNITY)
        )) {
            return;
        }
        final boolean hasEffects = !formula.effects().isEmpty();
        final boolean hasBehaviors = !formula.entityBehaviorKind().behaviors().isEmpty();
        if ((!hasEffects && !hasBehaviors)
            || !CustomBrewCloudRules.claim(cloud, target.getUUID(), level.getGameTime())) {
            return;
        }
        CustomBrewRuntime.applyEffectsTo(level, formula, target, cloud, cloud.getOwner());
        CustomBrewRuntime.handleCloudImpactTo(level, formula, target, cloud, cloud.getOwner());
        shrink(cloud, delivery);
    }

    private static boolean inside(final AreaEffectCloud cloud, final LivingEntity target) {
        final double x = target.getX() - cloud.getX();
        final double z = target.getZ() - cloud.getZ();
        final double radius = cloud.getRadius();
        return x * x + z * z <= radius * radius;
    }

    private static void shrink(final AreaEffectCloud cloud, final CustomBrewDelivery delivery) {
        final float radius = cloud.getRadius() + delivery.cloudRadiusOnUse(cloud.getRadius());
        if (radius < 0.5F) {
            cloud.discard();
        } else {
            cloud.setRadius(radius);
        }
    }
}
