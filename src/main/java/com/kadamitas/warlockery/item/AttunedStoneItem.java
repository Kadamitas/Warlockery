package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;

public final class AttunedStoneItem extends Item implements DroppedItemBehavior {
    private static final String POWER = "WarlockeryAltarPower";
    private final int defaultPower;

    public AttunedStoneItem(final Properties properties, final boolean charged) {
        super(properties.stacksTo(1));
        defaultPower = charged ? AttunedStoneRules.CAPACITY : 0;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof AltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }
        final ItemStack stack = context.getItemInHand();
        final int before = storedPower(stack);
        final AttunedStoneRules.Transfer transfer = context.getPlayer() != null && context.getPlayer().isSecondaryUseActive()
            ? AttunedStoneRules.deposit(before, altar.getPower(), altar.getCapacity())
            : AttunedStoneRules.withdraw(before, altar.getPower());
        if (!transfer.succeeded()) {
            if (context.getPlayer() != null && !context.getLevel().isClientSide()) {
                context.getPlayer().sendOverlayMessage(Component.translatable("message.warlockery.attuned_stone.no_power"));
            }
            return InteractionResult.FAIL;
        }
        if (!context.getLevel().isClientSide()) {
            if (transfer.stonePower() > before) {
                altar.consumePower(transfer.moved());
            } else {
                altar.receivePower(transfer.moved());
            }
            setStoredPower(stack, transfer.stonePower());
            if (context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(Component.translatable(
                    "message.warlockery.attuned_stone.power",
                    transfer.stonePower(),
                    AttunedStoneRules.CAPACITY
                ));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean tickDroppedItem(final ItemStack stack, final ItemEntity entity) {
        SpiritLocatorRuntime.tick(entity);
        return false;
    }

    public static int storedPower(final ItemStack stack) {
        final CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains(POWER)) {
            return AttunedStoneRules.bounded(data.getIntOr(POWER, 0));
        }
        return stack.getItem() instanceof AttunedStoneItem stone ? stone.defaultPower : 0;
    }

    static void setStoredPower(final ItemStack stack, final int power) {
        final int bounded = AttunedStoneRules.bounded(power);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putInt(POWER, bounded));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(
            "tooltip.warlockery.attuned_stone.power",
            bounded,
            AttunedStoneRules.CAPACITY
        ))));
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return storedPower(stack) > 0 || super.isFoil(stack);
    }
}
