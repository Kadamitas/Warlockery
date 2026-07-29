package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class FancifulCharmRuntime {
    private FancifulCharmRuntime() {
    }

    public static void handleDamage(final LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel)) {
            return;
        }
        final Entity attacker = event.getSource().getEntity() != null
            ? event.getSource().getEntity()
            : event.getSource().getDirectEntity();
        final boolean nightmareAttack = attacker != null
            && attacker.typeHolder().is(WarlockeryTags.EntityTypes.NIGHTMARES);
        final boolean charmCarried = player.getInventory().contains(stack ->
            stack.is(WarlockeryTags.Items.NIGHTMARE_GUARD_CHARMS)
        );
        if (FancifulCharmRules.resolve(nightmareAttack, charmCarried) != FancifulCharmRules.Outcome.SIDE_EFFECTS) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 240, 1));
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 1));
    }
}
