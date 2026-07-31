package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class CaneSwordItem extends Item {
    public static final Identifier CANE_MODEL = Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "canesword");
    public static final Identifier DRAWN_MODEL = Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "canesword_drawn");
    public static final double CANE_SPEED_BONUS = 0.15;
    public static final double DRAWN_ATTACK_DAMAGE = 7.0;
    public static final int DURABILITY = ToolMaterial.IRON.durability();
    private static final String DRAWN = "CaneSwordDrawn";
    private static final Identifier CANE_SPEED_ID = Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "cane_sword_speed");
    private static final ItemAttributeModifiers CANE_ATTRIBUTES = ItemAttributeModifiers.builder()
        .add(
            Attributes.MOVEMENT_SPEED,
            new AttributeModifier(CANE_SPEED_ID, CANE_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            EquipmentSlotGroup.MAINHAND
        )
        .build();
    private static final ItemAttributeModifiers DRAWN_ATTRIBUTES = ItemAttributeModifiers.builder()
        .add(
            Attributes.ATTACK_DAMAGE,
            new AttributeModifier(BASE_ATTACK_DAMAGE_ID, DRAWN_ATTACK_DAMAGE - 1.0, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND
        )
        .add(
            Attributes.ATTACK_SPEED,
            new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND
        )
        .build();

    public CaneSwordItem(final Properties properties) {
        super(properties);
    }

    public static Properties applyProperties(final Properties properties) {
        return properties.sword(ToolMaterial.IRON, 3.0F, -2.4F).attributes(CANE_ATTRIBUTES);
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        return toggleHeld(level, player, hand);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        return toggleHeld(context.getLevel(), player, context.getHand());
    }

    private static InteractionResult toggleHeld(final Level level, final Player player, final InteractionHand hand) {
        if (!level.isClientSide()) {
            final boolean drawn = toggle(player.getItemInHand(hand));
            player.sendOverlayMessage(Component.translatable(
                drawn ? "message.warlockery.cane_sword.drawn" : "message.warlockery.cane_sword.sheathed"
            ).withStyle(drawn ? ChatFormatting.RED : ChatFormatting.GREEN));
            level.playSound(
                null,
                player.blockPosition(),
                drawn ? SoundEvents.TRIPWIRE_CLICK_ON : SoundEvents.TRIPWIRE_CLICK_OFF,
                SoundSource.PLAYERS,
                0.7F,
                drawn ? 0.85F : 1.15F
            );
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean isDrawn(final ItemStack stack) {
        return isDrawn(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    public static void setDrawn(final ItemStack stack, final boolean drawn) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> writeState(data, drawn));
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes(drawn));
        stack.set(DataComponents.ITEM_MODEL, model(drawn));
    }

    public static boolean toggle(final ItemStack stack) {
        final boolean drawn = !isDrawn(stack);
        setDrawn(stack, drawn);
        return drawn;
    }

    public static ItemAttributeModifiers attributes(final boolean drawn) {
        return drawn ? DRAWN_ATTRIBUTES : CANE_ATTRIBUTES;
    }

    public static Identifier model(final boolean drawn) {
        return drawn ? DRAWN_MODEL : CANE_MODEL;
    }

    public static double attackDamage(final boolean drawn) {
        return attributes(drawn).compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
    }

    public static double movementSpeed(final boolean drawn, final double baseSpeed) {
        return attributes(drawn).compute(Attributes.MOVEMENT_SPEED, baseSpeed, EquipmentSlot.MAINHAND);
    }

    static boolean isDrawn(final CompoundTag data) {
        return data.getBooleanOr(DRAWN, false);
    }

    static void writeState(final CompoundTag data, final boolean drawn) {
        if (drawn) {
            data.putBoolean(DRAWN, true);
        } else {
            data.remove(DRAWN);
        }
    }
}
