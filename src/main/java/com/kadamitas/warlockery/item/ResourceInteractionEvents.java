package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.fabric.event.LivingDropsContext;
import com.kadamitas.warlockery.fabric.event.ProjectileImpactContext;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

public final class ResourceInteractionEvents {
    private ResourceInteractionEvents() {
    }

    public static void handleDrops(final LivingDropsContext event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity().typeHolder().is(WarlockeryTags.EntityTypes.WOLF_FORM_LAMB_SOURCES)
            && event.getSource().getEntity() instanceof Player player
            && isWolfFormLamb(true, SupernaturalState.getForm(player))
            && com.kadamitas.warlockery.transformation.SupernaturalProgression.level(
                player,
                com.kadamitas.warlockery.transformation.SupernaturalProgression.Path.WEREWOLF
            ) >= 2
            && com.kadamitas.warlockery.transformation.SupernaturalProgression.werewolfShape(player)
                != com.kadamitas.warlockery.transformation.WerewolfShape.HUMAN) {
            event.getDrops().add(new ItemEntity(
                level,
                event.getEntity().getX(),
                event.getEntity().getY(),
                event.getEntity().getZ(),
                new ItemStack(Items.MUTTON)
            ));
        }
        ArthanaHarvestRuntime.addDrops(event, level);
    }

    static boolean isWolfFormLamb(final boolean taggedSource, final SupernaturalForm form) {
        return taggedSource && form == SupernaturalForm.WEREWOLF;
    }

    public static void handleProjectileImpact(final ProjectileImpactContext event) {
        if (!(event.getProjectile() instanceof ThrowableItemProjectile projectile)
            || !projectile.getItem().is(WarlockeryTags.Items.THROWING_STONES)
            || !(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        final Entity target = hit.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        target.hurtServer(level, projectile.damageSources().thrown(projectile, projectile.getOwner()), 2.0F);
        final var movement = projectile.getDeltaMovement().normalize().scale(0.4);
        target.push(movement.x, 0.15, movement.z);
    }
}
