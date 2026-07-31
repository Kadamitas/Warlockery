package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;

public final class VillageSpiritItem extends Item {
    private static final String DIMENSION = "WarlockeryVillageDimension";
    private static final String POSITION = "WarlockeryVillagePosition";
    private final boolean vessel;

    public VillageSpiritItem(final Properties properties, final boolean vessel) {
        super(properties);
        this.vessel = vessel;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (!vessel || !context.getLevel().getBlockState(context.getClickedPos()).is(Blocks.BELL)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!level.isVillage(context.getClickedPos())) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(Component.translatable("message.warlockery.village_spirit.no_village"));
            }
            return InteractionResult.FAIL;
        }
        final ItemStack captured = context.getItemInHand().transmuteCopy(
            ModItems.ALL.get("ingredient_subdued_spirit_village").get(),
            1
        );
        writeVillage(captured, level, context.getClickedPos());
        if (context.getPlayer() != null && !context.getPlayer().hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        if (context.getPlayer() == null || !context.getPlayer().getInventory().add(captured)) {
            Block.popResource(level, context.getClickedPos().above(), captured);
        }
        if (context.getPlayer() != null) {
            context.getPlayer().sendOverlayMessage(Component.translatable("message.warlockery.village_spirit.captured"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean onEntityItemUpdate(final ItemStack stack, final ItemEntity entity) {
        if (vessel) {
            SpiritLocatorRuntime.tick(entity);
        }
        return false;
    }

    @Override
    public InteractionResult use(final Level level, final net.minecraft.world.entity.player.Player player, final InteractionHand hand) {
        final Optional<VillageBinding> binding = readVillage(player.getItemInHand(hand));
        if (binding.isEmpty()) {
            return InteractionResult.PASS;
        }
        final VillageBinding value = binding.orElseThrow();
        final int distance = value.dimension().equals(level.dimension().identifier().toString())
            ? Math.round((float) Math.sqrt(player.blockPosition().distSqr(value.position())))
            : -1;
        player.sendOverlayMessage(
            distance < 0
                ? Component.translatable("message.warlockery.village_spirit.other_dimension")
                : Component.translatable("message.warlockery.village_spirit.distance", distance)
        );
        return InteractionResult.SUCCESS;
    }

    public static void writeVillage(final ItemStack stack, final ServerLevel level, final BlockPos position) {
        writeVillage(stack, level.dimension().identifier().toString(), position);
    }

    static void writeVillage(final ItemStack stack, final String dimension, final BlockPos position) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> writeVillage(data, dimension, position));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.translatable("tooltip.warlockery.village_spirit.bound", position.getX(), position.getY(), position.getZ())
        )));
    }

    public static Optional<VillageBinding> readVillage(final ItemStack stack) {
        return readVillage(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    static void writeVillage(final CompoundTag data, final String dimension, final BlockPos position) {
        data.putString(DIMENSION, dimension);
        data.putLong(POSITION, position.asLong());
    }

    static Optional<VillageBinding> readVillage(final CompoundTag data) {
        final String dimension = data.getStringOr(DIMENSION, "");
        return dimension.isEmpty() || !data.contains(POSITION)
            ? Optional.empty()
            : Optional.of(new VillageBinding(dimension, BlockPos.of(data.getLongOr(POSITION, BlockPos.ZERO.asLong()))));
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return readVillage(stack).isPresent() || super.isFoil(stack);
    }

    public record VillageBinding(String dimension, BlockPos position) {
    }
}
