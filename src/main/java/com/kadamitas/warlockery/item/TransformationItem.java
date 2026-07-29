package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TransformationItem extends Item {
    private final SupernaturalForm form;
    private final boolean toggle;
    private final boolean consumed;

    public TransformationItem(final Properties properties, final SupernaturalForm form, final boolean toggle, final boolean consumed) {
        super(properties);
        this.form = form;
        this.toggle = toggle;
        this.consumed = consumed;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            final SupernaturalForm target = toggle && SupernaturalState.getForm(player) == form ? SupernaturalForm.NONE : form;
            SupernaturalState.setForm(player, target);
            player.sendSystemMessage(Component.translatable("message.warlockery.transformation." + target.name().toLowerCase()));
            if (consumed && !player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(stack, 20);
        }
        return InteractionResult.SUCCESS;
    }
}
