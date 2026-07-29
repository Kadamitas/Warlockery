package com.kadamitas.warlockery.block.entity;

import com.kadamitas.warlockery.block.DollShelfRules;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jspecify.annotations.Nullable;

public final class DollShelfBlockEntity extends BaseContainerBlockEntity {
    private static final Set<DollShelfBlockEntity> LOADED = ConcurrentHashMap.newKeySet();
    private NonNullList<ItemStack> items = NonNullList.withSize(DollShelfRules.CAPACITY, ItemStack.EMPTY);
    private LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> new InvWrapper(this));

    public DollShelfBlockEntity(final BlockPos position, final BlockState state) {
        super(ModBlockEntities.DOLL_SHELF.get(), position, state);
    }

    public static Stream<ItemStack> loadedDolls() {
        return LOADED.stream().filter(shelf -> !shelf.isRemoved())
            .flatMap(shelf -> shelf.items.stream())
            .filter(stack -> !stack.isEmpty());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.setChunkForced(worldPosition.getX() >> 4, worldPosition.getZ() >> 4, true);
        }
    }

    @Override
    public void setRemoved() {
        LOADED.remove(this);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
            && LOADED.stream().noneMatch(shelf -> shelf.level == level
                && (shelf.worldPosition.getX() >> 4) == (worldPosition.getX() >> 4)
                && (shelf.worldPosition.getZ() >> 4) == (worldPosition.getZ() >> 4))) {
            serverLevel.setChunkForced(worldPosition.getX() >> 4, worldPosition.getZ() >> 4, false);
        }
        itemHandler.invalidate();
        super.setRemoved();
    }

    @Override
    public int getContainerSize() {
        return DollShelfRules.CAPACITY;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(final NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public boolean canPlaceItem(final int slot, final ItemStack stack) {
        return stack.getItem() instanceof DollItem;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.warlockery.doll_shelf");
    }

    @Override
    protected AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this, 1);
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(DollShelfRules.CAPACITY, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    public <T> LazyOptional<T> getCapability(final Capability<T> capability, final @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER && !remove) {
            return itemHandler.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandler = LazyOptional.of(() -> new InvWrapper(this));
    }
}
