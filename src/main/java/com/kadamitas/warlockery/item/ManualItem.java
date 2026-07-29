package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class ManualItem extends Item {
    private final ManualProfile profile;

    public ManualItem(final Properties properties, final ManualProfile profile) {
        super(properties.stacksTo(1));
        this.profile = profile;
    }

    public ManualProfile profile() {
        return profile;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final boolean createsBiomeNote = "bookbiomes2".equals(profile.id()) && player.isShiftKeyDown();
        if (!createsBiomeNote) {
            if (level.isClientSide()) {
                ManualScreenBridge.open(profile);
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final boolean hasPaper = player.hasInfiniteMaterials()
            || player.getInventory().contains(stack -> stack.is(Items.PAPER));
        final ExtendedManualRules.Diagnostic diagnostic = ExtendedManualRules.diagnose(
            true,
            true,
            hasPaper
        );
        if (diagnostic == ExtendedManualRules.Diagnostic.MISSING_PAPER) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.biome_manual.missing_paper")
                .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (diagnostic == ExtendedManualRules.Diagnostic.CREATE_BIOME_NOTE) {
            if (!player.hasInfiniteMaterials()) {
                player.getInventory().clearOrCountMatchingItems(
                    stack -> stack.is(Items.PAPER),
                    1,
                    player.inventoryMenu.getCraftSlots()
                );
            }
            final ItemStack note = new ItemStack(ModItems.ALL.get("biomenote").get());
            if (!player.getInventory().add(note)) {
                player.drop(note, false);
            }
            player.sendOverlayMessage(Component.translatable("message.warlockery.biome_manual.note_created")
                .withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }
}
