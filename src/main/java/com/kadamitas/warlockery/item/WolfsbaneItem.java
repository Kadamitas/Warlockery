package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class WolfsbaneItem extends Item {
    public WolfsbaneItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        final SupernaturalForm form = target instanceof Player targetPlayer
            ? SupernaturalState.getForm(targetPlayer)
            : SupernaturalForm.NONE;
        final WolfsbaneRules.Diagnostic diagnostic = WolfsbaneRules.diagnose(
            target.typeHolder().is(WarlockeryTags.EntityTypes.WEREWOLVES),
            form
        );
        if (!player.level().isClientSide()) {
            final boolean detected = diagnostic == WolfsbaneRules.Diagnostic.LYCANTHROPY_DETECTED;
            player.sendOverlayMessage(Component.literal(
                detected ? "\u2713 Lycanthropy detected" : "No lycanthropy detected"
            ));
            if (detected) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
                stack.consume(1, player);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
