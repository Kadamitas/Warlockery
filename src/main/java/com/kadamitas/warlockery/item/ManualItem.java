package com.kadamitas.warlockery.item;

import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
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

    public boolean recordsBiomes() {
        return "ingredient_book_biomes".equals(profile.id()) || "bookbiomes2".equals(profile.id());
    }

    @Override
    public Component getName(final ItemStack stack) {
        if (!recordsBiomes()) {
            return super.getName(stack);
        }
        return BiomeNoteState.read(stack)
            .<Component>map(biome -> Component.translatable(
                "item.warlockery.biome_book.recorded",
                BiomeNoteState.displayName(biome)
            ))
            .orElseGet(() -> super.getName(stack));
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (!recordsBiomes() || !context.isSecondaryUseActive()) {
            return super.useOn(context);
        }
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final var biomeKey = context.getLevel().getBiome(context.getClickedPos()).unwrapKey();
        if (biomeKey.isEmpty()) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(Component.translatable("message.warlockery.biome_note.failed")
                    .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        final var biome = biomeKey.orElseThrow().identifier();
        final ItemStack book = context.getItemInHand();
        BiomeNoteState.write(book, biome);
        book.set(DataComponents.LORE, new ItemLore(java.util.List.of(
            Component.translatable("tooltip.warlockery.biome_note", BiomeNoteState.displayName(biome))
        )));
        if (context.getPlayer() != null) {
            context.getPlayer().sendOverlayMessage(Component.translatable(
                "message.warlockery.biome_note.recorded",
                BiomeNoteState.displayName(biome)
            ).withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack heldStack = player.getItemInHand(hand);
        if (ManualProgress.isTornPage(profile)) {
            return useImmortalPage(level, player, heldStack, profile);
        }
        if (level.isClientSide()) {
            ManualScreenBridge.open(ManualView.from(profile, heldStack));
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
