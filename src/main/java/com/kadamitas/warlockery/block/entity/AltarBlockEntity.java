package com.kadamitas.warlockery.block.entity;

import com.kadamitas.warlockery.block.AltarAttachmentRules;
import com.kadamitas.warlockery.block.AltarNatureRules;
import com.kadamitas.warlockery.block.AltarNatureRules.Source;
import com.kadamitas.warlockery.crafting.AltarUpgradeResolver;
import com.kadamitas.warlockery.crafting.AltarUpgradeResolver.Modifiers;
import com.kadamitas.warlockery.crafting.AltarUpgradeResolver.UpgradeClass;
import com.kadamitas.warlockery.crafting.AltarRangeIndex;
import com.kadamitas.warlockery.item.ResourceCompatibilityTags;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.RitualSessionData;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class AltarBlockEntity extends BlockEntity {
    private static final int SCAN_INTERVAL = 40;
    private static final int SEARCH_RADIUS = 16;
    private int power;
    private int escrowed;
    private int capacity;
    private boolean multiblockValid;
    private int connectedBlocks;
    private int environmentalPower;
    private double capacityMultiplier = 1.0;
    private int rechargeMultiplier = 1;
    private int activeUpgradeCount;
    private NonNullList<ItemStack> attachments = NonNullList.withSize(
        AltarAttachmentRules.CAPACITY,
        ItemStack.EMPTY
    );

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
        final AltarMultiblockLayout.Result layout = AltarMultiblockLayout.inspect(
            pos,
            candidate -> level.getBlockState(candidate).is(ModBlocks.ALTAR.get())
        );
        altar.connectedBlocks = layout.connectedBlocks();
        altar.multiblockValid = layout.valid();
        if (altar.multiblockValid) {
            final EnvironmentScan scan = altar.scanEnvironment(level, pos);
            final Modifiers modifiers = AltarUpgradeResolver.discoverItems(
                level,
                pos,
                SEARCH_RADIUS,
                Stream.concat(scan.upgrades().stream(), altar.attachmentUpgrades())
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
            altar.escrowed = 0;
            altar.capacityMultiplier = 1.0;
            altar.rechargeMultiplier = 1;
            altar.activeUpgradeCount = 0;
        }
        if (level instanceof ServerLevel ritualLevel && (altar.escrowed > 0 || altar.multiblockValid)) {
            altar.reconcileEscrow(RitualSessionData.get(ritualLevel).escrowedAt(pos));
        }
        altar.setChanged();
        if (!previous.equals(altar.getDisplay())) {
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
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

    private static Source powerSource(final BlockState state) {
        if (state.is(ResourceCompatibilityTags.Blocks.ALTAR_POWER_HEARTS)) {
            return Source.HEART;
        }
        if (state.is(BlockTags.LOGS)) {
            return Source.LOG;
        }
        if (state.is(BlockTags.LEAVES)) {
            return Source.LEAF;
        }
        if (state.is(BlockTags.FLOWERS)) {
            return Source.FLOWER;
        }
        if (state.is(BlockItemTags.SAPLINGS.block())) {
            return Source.SAPLING;
        }
        if (state.is(BlockTags.CROPS)) {
            return Source.CROP;
        }
        if (state.is(WarlockeryTags.Blocks.ALTAR_NATURAL_GROUND)) {
            return Source.GROUND;
        }
        if (state.is(WarlockeryTags.Blocks.ALTAR_NATURAL_WATER)) {
            return Source.WATER;
        }
        return state.is(WarlockeryTags.Blocks.ALTAR_NATURAL_POWER) ? Source.OTHER_NATURAL : null;
    }

    private static int powerValue(final Source source) {
        return switch (source) {
            case HEART -> 40;
            case FLOWER, SAPLING -> 4;
            case LOG -> 3;
            case LEAF, CROP, WATER -> 2;
            case GROUND, OTHER_NATURAL -> 1;
        };
    }

    public boolean consumePower(final int requested) {
        if (!multiblockValid || availablePower() < requested) {
            return false;
        }
        power -= requested;
        publish();
        return true;
    }

    /**
     * Power this altar has promised to a cast that has not finished. It is still physically here, which is why
     * the altar display keeps showing it, but nothing else may spend or promise it.
     */
    public int getEscrowedPower() {
        return escrowed;
    }

    /** Power free to be spent or promised right now. */
    public int availablePower() {
        return Math.max(0, power - escrowed);
    }

    /**
     * Promises power to a cast about to begin, or refuses. Nothing drains: a cast that never finishes has
     * taken nothing, and a cast that does finish settles the promise then.
     */
    public boolean escrowPower(final int requested) {
        if (!multiblockValid || requested < 0 || availablePower() < requested) {
            return false;
        }
        escrowed += requested;
        publish();
        return true;
    }

    /** Turns a promise into the real drain a finished cast has earned. */
    public void settleEscrow(final int amount) {
        final int settled = Math.clamp(amount, 0, escrowed);
        escrowed -= settled;
        power = Math.max(0, power - settled);
        publish();
    }

    /** Hands a promise back untouched, for a cast that ended without finishing. */
    public void releaseEscrow(final int amount) {
        escrowed = Math.max(0, escrowed - Math.max(0, amount));
        publish();
    }

    /**
     * Brings this altar's promises back in line with what live sessions actually claim.
     *
     * <p>Altars and ritual sessions are saved independently, so a world that stopped between the two writes,
     * or a session dropped on load for an unreadable id, can leave a promise with nobody behind it. Nothing
     * else would ever release it and the altar would look permanently poorer, so the sessions are treated as
     * the authority on what is owed.</p>
     */
    public void reconcileEscrow(final int claimed) {
        final int corrected = Math.clamp(claimed, 0, power);
        if (corrected != escrowed) {
            escrowed = corrected;
            publish();
        }
    }

    private void publish() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
        return attachments.stream().anyMatch(stack -> stack.is(WarlockeryTags.Items.ALTAR_RANGE_FOCI));
    }

    public boolean installRangeFocus(final ItemStack stack) {
        return stack.is(WarlockeryTags.Items.ALTAR_RANGE_FOCI) && installAttachment(stack);
    }

    public ItemStack removeRangeFocus() {
        return IntStream.range(0, attachments.size())
            .filter(slot -> attachments.get(slot).is(WarlockeryTags.Items.ALTAR_RANGE_FOCI))
            .findFirst()
            .stream()
            .mapToObj(this::removeAttachment)
            .findFirst()
            .orElse(ItemStack.EMPTY);
    }

    public boolean supportsAttachment(final ItemStack stack) {
        return stack.is(WarlockeryTags.Items.ALTAR_ATTACHMENTS)
            || stack.is(WarlockeryTags.Items.ALTAR_RANGE_FOCI)
            || AltarUpgradeResolver.classes(stack).findAny().isPresent();
    }

    public boolean installAttachment(final ItemStack stack) {
        final int occupied = (int) attachments.stream().filter(item -> !item.isEmpty()).count();
        final AltarAttachmentRules.Decision decision = AltarAttachmentRules.evaluate(
            supportsAttachment(stack),
            conflictsWithInstalledAttachment(stack),
            occupied
        );
        if (!decision.accepted()) {
            return false;
        }
        IntStream.range(0, attachments.size())
            .filter(slot -> attachments.get(slot).isEmpty())
            .findFirst()
            .ifPresent(slot -> attachments.set(slot, stack.copyWithCount(1)));
        synchronizeAttachments();
        return true;
    }

    public ItemStack removeLastAttachment() {
        final int slot = AltarAttachmentRules.lastOccupiedSlot(
            attachments.stream().map(stack -> !stack.isEmpty()).toList()
        );
        return slot < 0 ? ItemStack.EMPTY : removeAttachment(slot);
    }

    public List<ItemStack> removeAllAttachments() {
        final List<ItemStack> removed = attachments.stream()
            .filter(stack -> !stack.isEmpty())
            .map(ItemStack::copy)
            .toList();
        attachments = NonNullList.withSize(AltarAttachmentRules.CAPACITY, ItemStack.EMPTY);
        synchronizeAttachments();
        return removed;
    }

    public List<ItemStack> attachmentStacks() {
        return attachments.stream()
            .filter(stack -> !stack.isEmpty())
            .map(ItemStack::copy)
            .toList();
    }

    public int attachmentCount() {
        return (int) attachments.stream().filter(stack -> !stack.isEmpty()).count();
    }

    public Stream<UpgradeClass> attachmentUpgrades() {
        return attachments.stream().flatMap(AltarUpgradeResolver::classes);
    }

    private ItemStack removeAttachment(final int slot) {
        final ItemStack removed = attachments.set(slot, ItemStack.EMPTY);
        synchronizeAttachments();
        return removed;
    }

    private boolean conflictsWithInstalledAttachment(final ItemStack candidate) {
        if (candidate.is(WarlockeryTags.Items.ALTAR_RANGE_FOCI) && hasRangeFocus()) {
            return true;
        }
        final Set<AltarUpgradeResolver.UpgradeFamily> candidateFamilies = AltarUpgradeResolver.classes(candidate)
            .map(UpgradeClass::family)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return attachments.stream()
            .filter(stack -> !stack.isEmpty())
            .anyMatch(stack -> stack.is(candidate.getItem())
                || AltarUpgradeResolver.classes(stack).map(UpgradeClass::family).anyMatch(candidateFamilies::contains));
    }

    private void synchronizeAttachments() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            AltarRangeIndex.update(serverLevel, worldPosition, hasRangeFocus());
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
            hasRangeFocus()
        );
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        power = input.getIntOr("Power", 0);
        escrowed = input.getIntOr("Escrowed", 0);
        capacity = input.getIntOr("Capacity", 0);
        multiblockValid = input.getBooleanOr("MultiblockValid", false);
        connectedBlocks = input.getIntOr("ConnectedBlocks", 0);
        environmentalPower = input.getIntOr("EnvironmentalPower", 0);
        capacityMultiplier = input.getDoubleOr("CapacityMultiplier", 1.0);
        rechargeMultiplier = input.getIntOr("RechargeMultiplier", 1);
        activeUpgradeCount = input.getIntOr("ActiveUpgradeCount", 0);
        attachments = NonNullList.withSize(AltarAttachmentRules.CAPACITY, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, attachments);
        if (attachments.stream().allMatch(ItemStack::isEmpty)) {
            input.read("RangeFocus", ItemStack.CODEC)
                .filter(stack -> !stack.isEmpty())
                .ifPresent(stack -> attachments.set(0, stack));
        }
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Power", power);
        output.putInt("Escrowed", escrowed);
        output.putInt("Capacity", capacity);
        output.putBoolean("MultiblockValid", multiblockValid);
        output.putInt("ConnectedBlocks", connectedBlocks);
        output.putInt("EnvironmentalPower", environmentalPower);
        output.putDouble("CapacityMultiplier", capacityMultiplier);
        output.putInt("RechargeMultiplier", rechargeMultiplier);
        output.putInt("ActiveUpgradeCount", activeUpgradeCount);
        ContainerHelper.saveAllItems(output, attachments);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    private record EnvironmentScan(int power, Set<UpgradeClass> upgrades) {
    }

    private static final class EnvironmentAccumulator {
        private int power;
        private final EnumSet<UpgradeClass> upgrades = EnumSet.noneOf(UpgradeClass.class);
        private final EnumMap<Source, Integer> sourceCounts = new EnumMap<>(Source.class);

        private void accept(final BlockState state) {
            final Source source = powerSource(state);
            if (source != null) {
                final int seen = sourceCounts.getOrDefault(source, 0);
                power += AltarNatureRules.contribution(powerValue(source), seen);
                sourceCounts.put(source, seen + 1);
            }
            AltarUpgradeResolver.classes(state).forEach(upgrades::add);
        }

        private void combine(final EnvironmentAccumulator other) {
            power += other.power;
            upgrades.addAll(other.upgrades);
            other.sourceCounts.forEach((source, count) -> sourceCounts.merge(source, count, Integer::sum));
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
        double capacityMultiplier,
        int rechargeMultiplier,
        int activeUpgradeCount,
        boolean rangeFocused
    ) {
    }
}
