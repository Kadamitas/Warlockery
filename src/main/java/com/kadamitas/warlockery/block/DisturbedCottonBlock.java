package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.hex.HexKind;
import com.kadamitas.warlockery.ritual.hex.HexState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public final class DisturbedCottonBlock extends Block {
    public DisturbedCottonBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void playerDestroy(
        final Level level,
        final Player player,
        final BlockPos pos,
        final BlockState state,
        final @Nullable BlockEntity blockEntity,
        final ItemStack destroyedWith
    ) {
        super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
        if (!level.isClientSide() && qualifies(player, level)) {
            popResource(level, pos, new ItemStack(ModItems.ALL.get("ingredient_disturbed_cotton").get()));
            player.sendOverlayMessage(Component.translatable("message.warlockery.disturbed_cotton.harvested"));
        } else if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.disturbed_cotton.dormant"));
        }
    }

    public static boolean qualifies(final Player player, final Level level) {
        return DisturbedCottonHarvestRules.qualifies(
            level.isDarkOutside(),
            HexState.isActive(player, HexKind.WAKING_NIGHTMARE) || player.hasEffect(MobEffects.DARKNESS),
            !level.getEntitiesOfClass(
                Mob.class,
                new AABB(player.blockPosition()).inflate(24.0),
                entity -> entity.typeHolder().is(WarlockeryTags.EntityTypes.NIGHTMARES)
            ).isEmpty()
        );
    }
}
