package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class NecromanticFocusItem extends Item {
    public NecromanticFocusItem(final Properties properties) {
        super(properties.stacksTo(1).durability(256));
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        final boolean commandable = target.typeHolder().is(WarlockeryTags.EntityTypes.NECROMANTIC_COMMANDABLES);
        final boolean boundElsewhere = CreatureBehaviorState.owner(target)
            .filter(owner -> !owner.equals(player.getUUID()))
            .isPresent();
        final UtilityDecision decision = NecromancyRules.command(commandable, boundElsewhere);
        if (!decision.success()) {
            show(player, decision);
            return InteractionResult.FAIL;
        }
        if (!player.level().isClientSide()) {
            CreatureBehaviorState.bind(target, player.getUUID());
            target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 0));
            if (target instanceof Mob mob) {
                mob.setTarget(null);
                mob.setPersistenceRequired();
            }
            stack.hurtAndBreak(1, player, hand);
            show(player, decision);
        }
        return InteractionResult.SUCCESS;
    }

    private static void show(final Player player, final UtilityDecision decision) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(decision.messageKey("necromancy"))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
