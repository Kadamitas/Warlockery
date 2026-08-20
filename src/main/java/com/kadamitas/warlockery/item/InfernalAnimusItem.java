package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class InfernalAnimusItem extends Item {
    public InfernalAnimusItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (!target.typeHolder().is(WarlockeryTags.EntityTypes.DEMONS)) {
            return InteractionResult.PASS;
        }
        if (target.typeHolder().is(
            com.kadamitas.warlockery.magic.MagicCompatibilityTags.INFERNAL_ENTHRALLMENT_IMMUNE
        )) {
            return InteractionResult.PASS;
        }
        final java.util.Optional<java.util.UUID> directOwner =
            com.kadamitas.warlockery.entity.CreatureBehaviorState.owner(target);
        if (directOwner.isPresent() && !directOwner.orElseThrow().equals(player.getUUID())) {
            if (!player.level().isClientSide()) {
                player.sendSystemMessage(Component.translatable(
                    "message.warlockery.creature.bound_elsewhere", target.getDisplayName()
                ));
            }
            return InteractionResult.FAIL;
        }
        if (!player.level().isClientSide()) {
            WarlockeryEntityData.get(target).putString(InfernalPactEffects.OWNER_KEY, player.getStringUUID());
            if (target instanceof Mob mob) {
                mob.setPersistenceRequired();
                mob.setTarget(null);
            }
            player.sendSystemMessage(Component.translatable("message.warlockery.infernal_animus.bound", target.getDisplayName()));
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
