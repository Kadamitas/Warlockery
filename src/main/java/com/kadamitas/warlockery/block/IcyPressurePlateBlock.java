package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public final class IcyPressurePlateBlock extends PressurePlateBlock {
    public IcyPressurePlateBlock(final BlockBehaviour.Properties properties) {
        super(BlockSetType.STONE, properties);
    }

    @Override
    protected int getSignalStrength(final Level level, final BlockPos pos) {
        return level.getEntitiesOfClass(
            Player.class,
            TOUCH_AABB.move(pos),
            player -> !player.isSpectator()
                && !player.isIgnoringBlockTriggers()
                && player.getItemBySlot(EquipmentSlot.FEET).is(WarlockeryTags.Items.ICE_PRESSURE_PLATE_ACTIVATORS)
        ).isEmpty() ? 0 : 15;
    }
}
