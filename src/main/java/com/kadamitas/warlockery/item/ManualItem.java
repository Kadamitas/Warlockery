package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModItems;
import java.util.Optional;
import java.util.stream.IntStream;
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
        final ItemStack heldStack = player.getItemInHand(hand);
        if (ManualProgress.isTornPage(profile)) {
            return useImmortalPage(level, player, heldStack, profile);
        }
        final boolean createsBiomeNote = "bookbiomes2".equals(profile.id()) && player.isShiftKeyDown();
        if (!createsBiomeNote) {
            if (level.isClientSide()) {
                ManualScreenBridge.open(ManualView.from(profile, heldStack));
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

    private static InteractionResult useImmortalPage(
        final Level level,
        final Player player,
        final ItemStack page,
        final ManualProfile pageProfile
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final Optional<ItemStack> observations = observationsIn(player);
        if (observations.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.immortal_page.missing_book")
                .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        final ItemStack book = observations.orElseThrow();
        final ManualProfile bookProfile = ((ManualItem) book.getItem()).profile();
        final ManualProgress.RevealResult result = ManualProgress.insertTornPage(
            bookProfile,
            book,
            pageProfile,
            page,
            player.hasInfiniteMaterials()
        );
        if (result.status() == ManualProgress.RevealStatus.COMPLETE) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.immortal_page.complete")
                .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }
        if (result.status() != ManualProgress.RevealStatus.REVEALED) {
            return InteractionResult.FAIL;
        }
        final String revealedSection = result.section().orElseThrow();
        player.sendOverlayMessage(Component.translatable(
            "message.warlockery.immortal_page.revealed",
            Component.translatable(bookProfile.translatedSectionTitleKey(revealedSection))
        ).withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    private static Optional<ItemStack> observationsIn(final Player player) {
        return IntStream.range(0, player.getInventory().getContainerSize())
            .mapToObj(player.getInventory()::getItem)
            .filter(stack -> stack.getItem() instanceof ManualItem manual
                && ManualProgress.isObservations(manual.profile()))
            .findFirst();
    }
}
