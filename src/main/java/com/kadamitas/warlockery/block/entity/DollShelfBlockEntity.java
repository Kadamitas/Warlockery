package com.kadamitas.warlockery.block.entity;

import com.kadamitas.warlockery.block.DollShelfRules;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.menu.DollShelfMenu;
import com.kadamitas.warlockery.item.DollMendingSchedule;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModChunkTickets;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
    private static final Comparator<DollShelfBlockEntity> SHELF_ORDER = Comparator
        .comparing((DollShelfBlockEntity shelf) -> ((ServerLevel) shelf.level).dimension().identifier().toString())
        .thenComparingLong(shelf -> shelf.worldPosition.asLong());
    private NonNullList<ItemStack> items = NonNullList.withSize(DollShelfRules.CAPACITY, ItemStack.EMPTY);
    private LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> new InvWrapper(this));

    public DollShelfBlockEntity(final BlockPos position, final BlockState state) {
        super(ModBlockEntities.DOLL_SHELF.get(), position, state);
    }

    public static void serverTick(
        final Level level,
        final BlockPos position,
        final BlockState state,
        final DollShelfBlockEntity shelf
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        final MinecraftServer server = serverLevel.getServer();
        if (DollMendingSchedule.forServer(server).beginShelfScan(server.getTickCount())) {
            processShelvedMending(server);
        }
    }

    public static Stream<ItemStack> loadedDolls(final MinecraftServer server) {
        return loadedShelves(server)
            .flatMap(shelf -> shelf.items.stream())
            .filter(stack -> stack.getItem() instanceof DollItem);
    }

    public static void markContainingShelfChanged(final ItemStack stack, final MinecraftServer server) {
        loadedShelves(server)
            .filter(shelf -> shelf.items.stream().anyMatch(stored -> stored == stack))
            .findFirst()
            .ifPresent(DollShelfBlockEntity::setChanged);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
        updateChunkTicket();
    }

    @Override
    public void setRemoved() {
        LOADED.remove(this);
        itemHandler.invalidate();
        super.setRemoved();
    }

    public void releaseChunkTicket() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        LOADED.remove(this);
        updateChunkTicket(serverLevel, chunkX(), chunkZ());
    }

    public boolean requiresChunkTicket() {
        return items.stream().anyMatch(stack -> stack.getItem() instanceof DollItem && DollItem.isBound(stack));
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
        return stack.is(WarlockeryTags.Items.DOLL_SHELF_CONTENTS)
            || stack.getItem() instanceof DollItem
            || stack.is(WarlockeryTags.Items.SYMPATHETIC_CONTAINERS);
    }

    @Override
    public ItemStack removeItemNoUpdate(final int slot) {
        final ItemStack removed = super.removeItemNoUpdate(slot);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public void clearContent() {
        super.clearContent();
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        updateChunkTicket();
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.warlockery.doll_shelf");
    }

    @Override
    protected AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
        return new DollShelfMenu(containerId, inventory, this);
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

    private void updateChunkTicket() {
        if (level instanceof ServerLevel serverLevel && LOADED.contains(this)) {
            updateChunkTicket(serverLevel, chunkX(), chunkZ());
        }
    }

    private static void processShelvedMending(final MinecraftServer server) {
        loadedShelves(server)
            .flatMap(shelf -> shelf.items.stream())
            .filter(stack -> stack.getItem() instanceof DollItem)
            .forEach(stack -> SympatheticBinding.read(stack)
                .flatMap(binding -> binding.resolve(server))
                .filter(net.minecraft.server.level.ServerPlayer.class::isInstance)
                .map(net.minecraft.server.level.ServerPlayer.class::cast)
                .filter(net.minecraft.server.level.ServerPlayer::isAlive)
                .ifPresent(player -> DollItem.tryMendBoundEquipment(
                    stack,
                    (ServerLevel) player.level(),
                    player
                )));
    }

    private static Stream<DollShelfBlockEntity> loadedShelves(final MinecraftServer server) {
        return LOADED.stream()
            .filter(shelf -> !shelf.isRemoved())
            .filter(shelf -> shelf.level instanceof ServerLevel shelfLevel && shelfLevel.getServer() == server)
            .sorted(SHELF_ORDER);
    }

    private static void updateChunkTicket(final ServerLevel level, final int chunkX, final int chunkZ) {
        final boolean required = LOADED.stream().anyMatch(shelf -> shelf.level == level
            && shelf.chunkX() == chunkX
            && shelf.chunkZ() == chunkZ
            && shelf.requiresChunkTicket());
        final ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
        if (required) {
            level.getChunkSource().addTicketWithRadius(ModChunkTickets.DOLL_SHELF.get(), chunk, 0);
        } else {
            level.getChunkSource().removeTicketWithRadius(ModChunkTickets.DOLL_SHELF.get(), chunk, 0);
        }
    }

    private int chunkX() {
        return worldPosition.getX() >> 4;
    }

    private int chunkZ() {
        return worldPosition.getZ() >> 4;
    }
}
