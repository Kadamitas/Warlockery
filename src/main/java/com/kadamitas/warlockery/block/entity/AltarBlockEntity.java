package com.kadamitas.warlockery.block.entity;

import com.kadamitas.warlockery.crafting.AltarUpgradeResolver;
import com.kadamitas.warlockery.crafting.AltarUpgradeResolver.Modifiers;
import com.kadamitas.warlockery.crafting.AltarUpgradeResolver.UpgradeClass;
import com.kadamitas.warlockery.crafting.AltarRangeIndex;
import com.kadamitas.warlockery.item.ResourceCompatibilityTags;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class AltarBlockEntity extends BlockEntity {
    private static final int SCAN_INTERVAL = 40;
    private static final int SEARCH_RADIUS = 8;
    private int power;
    private int capacity;
    private boolean multiblockValid;
    private int connectedBlocks;
    private int environmentalPower;
    private int capacityMultiplier = 1;
    private int rechargeMultiplier = 1;
    private int activeUpgradeCount;
    private ItemStack rangeFocus = ItemStack.EMPTY;
    private boolean rangeFocused;

    public AltarBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.ALTAR.get(), pos, state);
    }

    public static void serverTick(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final AltarBlockEntity altar
    ) {
        if (level.getGameTime() % SCAN_INTERVAL != 0) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            AltarRangeIndex.update(serverLevel, pos, altar.hasRangeFocus());
        }
        final AltarDisplay previous = altar.getDisplay();
        altar.connectedBlocks = altar.countConnectedAltarBlocks(level, pos);
        altar.multiblockValid = altar.connectedBlocks >= 6;
        if (altar.multiblockValid) {
            final EnvironmentScan scan = altar.scanEnvironment(level, pos);
            final Modifiers modifiers = AltarUpgradeResolver.discoverItems(
                level,
                pos,
                SEARCH_RADIUS,
                scan.upgrades().stream()
            );
            altar.environmentalPower = scan.power();
            altar.capacityMultiplier = modifiers.capacityMultiplier();
            altar.rechargeMultiplier = modifiers.rechargeMultiplier();
            altar.activeUpgradeCount = modifiers.activeClasses().size();
            final int baseCapacity = Math.max(1_000, altar.environmentalPower * 40);
            final int baseRecharge = Math.max(1, altar.environmentalPower / 10);
            altar.capacity = modifiers.applyCapacity(baseCapacity);
            altar.power = Math.min(altar.capacity, altar.power + modifiers.applyRecharge(baseRecharge));
        } else {
            altar.environmentalPower = 0;
            altar.capacity = 0;
            altar.power = 0;
            altar.capacityMultiplier = 1;
            altar.rechargeMultiplier = 1;
            altar.activeUpgradeCount = 0;
        }
        altar.setChanged();
        if (!previous.equals(altar.getDisplay())) {
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private int countConnectedAltarBlocks(final Level level, final BlockPos origin) {
        final Set<BlockPos> visited = new HashSet<>();
        final Set<BlockPos> frontier = new HashSet<>(Set.of(origin));
        while (!frontier.isEmpty() && visited.size() < 6) {
            final BlockPos current = frontier.iterator().next();
            frontier.remove(current);
            if (visited.contains(current) || !level.getBlockState(current).is(ModBlocks.ALTAR.get())) {
                continue;
            }
            visited.add(current);
            frontier.add(current.north());
            frontier.add(current.south());
            frontier.add(current.east());
            frontier.add(current.west());
        }
        return visited.size();
    }

    private EnvironmentScan scanEnvironment(final Level level, final BlockPos origin) {
        return IntStream.rangeClosed(-SEARCH_RADIUS, SEARCH_RADIUS)
            .boxed()
            .flatMap(x -> IntStream.rangeClosed(-4, 6).mapToObj(y -> new int[]{x, y}))
            .flatMap(xy -> IntStream.rangeClosed(-SEARCH_RADIUS, SEARCH_RADIUS)
                .mapToObj(z -> origin.offset(xy[0], xy[1], z)))
            .map(level::getBlockState)
            .collect(EnvironmentAccumulator::new, EnvironmentAccumulator::accept, EnvironmentAccumulator::combine)
            .finish();
    }

    private static int powerValue(final BlockState state) {
        if (state.is(ResourceCompatibilityTags.Blocks.ALTAR_POWER_HEARTS)) {
            return 40;
        }
        if (state.is(BlockTags.LOGS)) {
            return 3;
        }
        if (state.is(BlockTags.LEAVES)) {
            return 2;
        }
        if (state.is(BlockTags.FLOWERS) || state.is(BlockItemTags.SAPLINGS.block())) {
            return 4;
        }
        return state.isAir() ? 0 : 1;
    }

    public boolean consumePower(final int requested) {
        if (!multiblockValid || power < requested) {
            return false;
        }
        power -= requested;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    public int receivePower(final int offered) {
        if (!multiblockValid || offered <= 0 || power >= capacity) {
            return 0;
        }
        final int accepted = Math.min(offered, capacity - power);
        power += accepted;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return accepted;
    }

    public int getPower() {
        return power;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isMultiblockValid() {
        return multiblockValid;
    }

    public boolean hasRangeFocus() {
        return rangeFocused;
    }

    public boolean installRangeFocus(final ItemStack stack) {
        if (!rangeFocus.isEmpty() || !stack.is(WarlockeryTags.Items.ALTAR_RANGE_FOCI)) {
            return false;
        }
        rangeFocus = stack.copyWithCount(1);
        rangeFocused = true;
        synchronizeRangeFocus();
        return true;
    }

    public ItemStack removeRangeFocus() {
        final ItemStack removed = rangeFocus;
        rangeFocus = ItemStack.EMPTY;
        rangeFocused = false;
        synchronizeRangeFocus();
        return removed;
    }

    private void synchronizeRangeFocus() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            AltarRangeIndex.update(serverLevel, worldPosition, rangeFocused);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public AltarDisplay getDisplay() {
        return new AltarDisplay(
            connectedBlocks,
            multiblockValid,
            environmentalPower,
            power,
            capacity,
            capacityMultiplier,
            rechargeMultiplier,
            activeUpgradeCount,
            rangeFocused
        );
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        power = input.getIntOr("Power", 0);
        capacity = input.getIntOr("Capacity", 0);
        multiblockValid = input.getBooleanOr("MultiblockValid", false);
        connectedBlocks = input.getIntOr("ConnectedBlocks", 0);
        environmentalPower = input.getIntOr("EnvironmentalPower", 0);
        capacityMultiplier = input.getIntOr("CapacityMultiplier", 1);
        rechargeMultiplier = input.getIntOr("RechargeMultiplier", 1);
        activeUpgradeCount = input.getIntOr("ActiveUpgradeCount", 0);
        rangeFocus = input.read("RangeFocus", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        rangeFocused = input.getBooleanOr("RangeFocused", !rangeFocus.isEmpty());
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Power", power);
        output.putInt("Capacity", capacity);
        output.putBoolean("MultiblockValid", multiblockValid);
        output.putInt("ConnectedBlocks", connectedBlocks);
        output.putInt("EnvironmentalPower", environmentalPower);
        output.putInt("CapacityMultiplier", capacityMultiplier);
        output.putInt("RechargeMultiplier", rechargeMultiplier);
        output.putInt("ActiveUpgradeCount", activeUpgradeCount);
        if (!rangeFocus.isEmpty()) {
            output.store("RangeFocus", ItemStack.CODEC, rangeFocus);
        }
        output.putBoolean("RangeFocused", rangeFocused);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Power", power);
        tag.putInt("Capacity", capacity);
        tag.putBoolean("MultiblockValid", multiblockValid);
        tag.putInt("ConnectedBlocks", connectedBlocks);
        tag.putInt("EnvironmentalPower", environmentalPower);
        tag.putInt("CapacityMultiplier", capacityMultiplier);
        tag.putInt("RechargeMultiplier", rechargeMultiplier);
        tag.putInt("ActiveUpgradeCount", activeUpgradeCount);
        tag.putBoolean("RangeFocused", rangeFocused);
        return tag;
    }

    private record EnvironmentScan(int power, Set<UpgradeClass> upgrades) {
    }

    private static final class EnvironmentAccumulator {
        private int power;
        private final EnumSet<UpgradeClass> upgrades = EnumSet.noneOf(UpgradeClass.class);

        private void accept(final BlockState state) {
            power += powerValue(state);
            AltarUpgradeResolver.classes(state).forEach(upgrades::add);
        }

        private void combine(final EnvironmentAccumulator other) {
            power += other.power;
            upgrades.addAll(other.upgrades);
        }

        private EnvironmentScan finish() {
            return new EnvironmentScan(power, Set.copyOf(upgrades));
        }
    }

    public record AltarDisplay(
        int connectedBlocks,
        boolean valid,
        int environmentalPower,
        int power,
        int capacity,
        int capacityMultiplier,
        int rechargeMultiplier,
        int activeUpgradeCount,
        boolean rangeFocused
    ) {
    }
}
