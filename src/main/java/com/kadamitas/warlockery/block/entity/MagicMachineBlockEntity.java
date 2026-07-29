package com.kadamitas.warlockery.block.entity;

import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.crafting.AltarPowerNetwork;
import com.kadamitas.warlockery.crafting.BrazierEffectRuntime;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.MachineDisplay;
import com.kadamitas.warlockery.crafting.MachineProfile;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineSlotLayout;
import com.kadamitas.warlockery.crafting.MachineStatus;
import com.kadamitas.warlockery.crafting.MachineUpgradeRules;
import com.kadamitas.warlockery.crafting.LeonardBrewingRisk;
import com.kadamitas.warlockery.block.MagicMachineBlock;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.item.EquipmentSetEffects;
import com.kadamitas.warlockery.brew.custom.CustomBrewCauldronState;
import com.kadamitas.warlockery.brew.custom.CustomBrewComposer;
import com.kadamitas.warlockery.brew.custom.CustomBrewDefinitionManager;
import com.kadamitas.warlockery.brew.custom.CustomBrewRuntime;
import com.kadamitas.warlockery.brew.BodegaBrewingRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jspecify.annotations.Nullable;

public final class MagicMachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    private static final int INVENTORY_SIZE = 9;

    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int burnTime;
    private int progress;
    private String activeRecipe = "";
    private long cachedRevision = -1;
    private int cachedAltarPower = -1;
    private boolean recipeDirty = true;
    private Optional<MachineRecipeManager.Match> cachedRecipe = Optional.empty();
    private MachineDisplay machineDisplay = MachineDisplay.EMPTY;
    private CustomBrewCauldronState customBrewState = CustomBrewCauldronState.EMPTY;
    private int customBrewProgress;
    private boolean brazierIgnited;
    private MachineSlotLayout slotLayout;
    private LazyOptional<? extends IItemHandler>[] itemHandlers = createItemHandlers();
    private final FluidTank fluidTank = new FluidTank(4_000) {
        @Override
        protected void onContentsChanged() {
            invalidateRecipeCache();
            setChanged();
        }
    };
    private LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(() -> fluidTank);

    public MagicMachineBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.MAGIC_MACHINE.get(), pos, state);
    }

    public static void serverTick(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final MagicMachineBlockEntity machine
    ) {
        if (machine.burnTime > 0) {
            machine.burnTime--;
        }

        final MachineProfile profile = machine.machineProfile();
        if ("brazier".equals(profile.recipeType())) {
            machine.brazierIgnited |= level.hasNeighborSignal(pos);
            if (!machine.brazierIgnited) {
                machine.resetProgress();
                setLit(level, pos, state, false);
                machine.updateMachineDisplay(level, pos, state);
                return;
            }
        }
        if ("cauldron".equals(profile.recipeType()) && machine.tickCustomBrew(level, pos, state, profile)) {
            return;
        }
        if (profile.requiresExternalHeat() && !isHeated(level, pos)) {
            machine.resetProgress();
            setLit(level, pos, state, false);
            machine.updateMachineDisplay(level, pos, state);
            return;
        }

        final var match = machine.findRecipe(profile, level, pos);
        if (match.isEmpty()) {
            machine.resetProgress();
            setLit(level, pos, state, machine.burnTime > 0);
            machine.updateMachineDisplay(level, pos, state);
            return;
        }

        final MachineRecipeDefinition recipe = match.get().recipe();
        if (level instanceof ServerLevel serverLevel
            && !BodegaBrewingRules.allows(serverLevel, pos, match.get().id())) {
            machine.resetProgress();
            setLit(level, pos, state, false);
            machine.updateMachineDisplay(level, pos, state);
            return;
        }
        final MachineUpgradeRules.Upgrade upgrade = MachineUpgradeRules.around(level, pos, profile);
        final List<ItemStack> upgradedOutputs = MachineUpgradeRules.enhanceOutputs(
            MachineRecipeManager.INSTANCE.createOutputs(recipe), upgrade
        );
        final List<ItemStack> outputs = level instanceof ServerLevel serverLevel
            ? EquipmentSetEffects.enhanceMachineOutputs(serverLevel, pos, profile.recipeType(), upgradedOutputs)
            : upgradedOutputs;
        if (outputs.stream().anyMatch(ItemStack::isEmpty) || !machine.canAccept(outputs, profile)) {
            machine.resetProgress();
            setLit(level, pos, state, machine.burnTime > 0);
            machine.updateMachineDisplay(level, pos, state);
            return;
        }

        if (recipe.requiresFuel() && machine.burnTime <= 0 && !machine.consumeFuel(level, profile)) {
            machine.resetProgress();
            setLit(level, pos, state, false);
            machine.updateMachineDisplay(level, pos, state);
            return;
        }

        final String recipeId = match.get().id().toString();
        if (!recipeId.equals(machine.activeRecipe)) {
            machine.progress = 0;
            machine.activeRecipe = recipeId;
        }
        machine.progress += upgrade.progressPerTick();
        setLit(level, pos, state, true);

        if (machine.progress >= recipe.processingTime()) {
            if (level instanceof ServerLevel serverLevel
                && !AltarPowerNetwork.consume(serverLevel, pos, recipe.altarPower())) {
                machine.resetProgress();
                machine.invalidateRecipeCache();
                machine.updateMachineDisplay(level, pos, state);
                return;
            }
            MachineRecipeManager.INSTANCE.consumeInputs(recipe, machine.items, profile.inputSlots());
            MachineRecipeManager.INSTANCE.consumeFluid(recipe, machine.fluidTank);
            machine.insertOutputs(outputs, profile);
            if (level instanceof ServerLevel serverLevel && "cauldron".equals(profile.recipeType())) {
                LeonardBrewingRisk.apply(serverLevel, pos);
            }
            if (level instanceof ServerLevel serverLevel && "brazier".equals(profile.recipeType())) {
                BrazierEffectRuntime.apply(serverLevel, pos, match.get().id());
                machine.brazierIgnited = false;
            }
            machine.progress = 0;
            machine.activeRecipe = "";
            machine.recipeDirty = true;
            level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 0.9F);
        }
        machine.setChanged();
        machine.updateMachineDisplay(level, pos, state);
    }

    private static void setLit(final Level level, final BlockPos pos, final BlockState state, final boolean lit) {
        if (state.hasProperty(MagicMachineBlock.LIT) && state.getValue(MagicMachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(MagicMachineBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private boolean tickCustomBrew(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final MachineProfile profile
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        final int altarPower = AltarPowerNetwork.available(serverLevel, pos);
        final CustomBrewDefinitionManager.Inspection inspection = CustomBrewDefinitionManager.INSTANCE.inspect(
            items,
            profile.inputSlots(),
            fluidTank.getFluid(),
            altarPower,
            isHeated(level, pos),
            items.get(profile.outputStart()).isEmpty(),
            customBrewState.engaged()
        );
        if (!inspection.engaged()) {
            updateCustomBrewState(level, pos, state, CustomBrewCauldronState.EMPTY);
            customBrewProgress = 0;
            return false;
        }

        resetProgress();
        machineDisplay = MachineDisplay.EMPTY;
        CustomBrewCauldronState next = inspection.state();
        if (!next.ready()) {
            customBrewProgress = 0;
            setLit(level, pos, state, false);
            updateCustomBrewState(level, pos, state, next);
            return true;
        }

        customBrewProgress++;
        final int processingTime = customProcessingTime(next);
        next = next.withProgress(Math.clamp((customBrewProgress * 100 / processingTime) / 5 * 5, 0, 100));
        setLit(level, pos, state, true);
        if (customBrewProgress >= processingTime) {
            if (!AltarPowerNetwork.consume(serverLevel, pos, next.requiredPower())) {
                customBrewProgress = 0;
                updateCustomBrewState(level, pos, state, next);
                return true;
            }
            final ItemStack baseOutput = next.formula().map(CustomBrewRuntime::createOutput).orElse(ItemStack.EMPTY);
            final ItemStack output = EquipmentSetEffects.enhanceMachineOutputs(
                serverLevel, pos, profile.recipeType(), List.of(baseOutput)
            ).stream().findFirst().orElse(ItemStack.EMPTY);
            if (output.isEmpty() || !items.get(profile.outputStart()).isEmpty()) {
                customBrewProgress = 0;
                updateCustomBrewState(level, pos, state, next);
                return true;
            }
            items.stream().limit(profile.inputSlots()).filter(stack -> !stack.isEmpty()).forEach(stack -> stack.shrink(1));
            fluidTank.drain(CustomBrewComposer.WATER_REQUIRED, IFluidHandler.FluidAction.EXECUTE);
            items.set(profile.outputStart(), output);
            LeonardBrewingRisk.apply(serverLevel, pos);
            customBrewProgress = 0;
            next = CustomBrewCauldronState.EMPTY;
            recipeDirty = true;
            level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.9F, 1.1F);
        }
        setChanged();
        updateCustomBrewState(level, pos, state, next);
        return true;
    }

    private static int customProcessingTime(final CustomBrewCauldronState state) {
        return state.formula()
            .map(formula -> Math.clamp(80 + formula.components().size() * 10, 100, 320))
            .orElse(100);
    }

    private void updateCustomBrewState(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final CustomBrewCauldronState next
    ) {
        if (!next.equals(customBrewState)) {
            customBrewState = next;
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private boolean consumeFuel(final Level level, final MachineProfile profile) {
        if (!profile.hasFuelSlot()) {
            return false;
        }
        final ItemStack fuel = items.get(profile.fuelSlot());
        final int duration = level.fuelValues().burnDuration(fuel);
        if (duration <= 0) {
            return false;
        }
        burnTime = duration;
        final ItemStackTemplate remainder = fuel.getCraftingRemainder();
        if (fuel.getCount() == 1 && remainder != null) {
            items.set(profile.fuelSlot(), remainder.create());
        } else {
            fuel.shrink(1);
        }
        return true;
    }

    private static boolean isHeated(final Level level, final BlockPos pos) {
        final BlockState heat = level.getBlockState(pos.below());
        return heat.is(WarlockeryTags.Blocks.MACHINE_HEAT_SOURCES)
            && (!heat.hasProperty(BlockStateProperties.LIT) || heat.getValue(BlockStateProperties.LIT));
    }

    private boolean canAccept(final List<ItemStack> outputs, final MachineProfile profile) {
        final int outputStart = profile.outputStart();
        if (outputs.size() > INVENTORY_SIZE - outputStart) {
            return false;
        }
        for (int index = 0; index < outputs.size(); index++) {
            final ItemStack existing = items.get(outputStart + index);
            final ItemStack output = outputs.get(index);
            if (!existing.isEmpty()
                && (!ItemStack.isSameItemSameComponents(existing, output)
                    || existing.getCount() + output.getCount() > existing.getMaxStackSize())) {
                return false;
            }
        }
        return true;
    }

    private void insertOutputs(final List<ItemStack> outputs, final MachineProfile profile) {
        final int outputStart = profile.outputStart();
        for (int index = 0; index < outputs.size(); index++) {
            final int slot = outputStart + index;
            final ItemStack output = outputs.get(index);
            if (items.get(slot).isEmpty()) {
                items.set(slot, output.copy());
            } else {
                items.get(slot).grow(output.getCount());
            }
        }
    }

    private void resetProgress() {
        if (progress != 0 || !activeRecipe.isEmpty()) {
            progress = 0;
            activeRecipe = "";
            setChanged();
        }
    }

    private Optional<MachineRecipeManager.Match> findRecipe(
        final MachineProfile profile,
        final Level level,
        final BlockPos pos
    ) {
        final MachineRecipeManager manager = MachineRecipeManager.INSTANCE;
        final int altarPower = level instanceof ServerLevel serverLevel ? AltarPowerNetwork.available(serverLevel, pos) : 0;
        if (recipeDirty || cachedRevision != manager.revision() || cachedAltarPower != altarPower) {
            cachedRecipe = manager.find(
                profile,
                items,
                profile.supportsFluids() ? fluidTank.getFluid() : FluidStack.EMPTY,
                altarPower
            );
            cachedRevision = manager.revision();
            cachedAltarPower = altarPower;
            recipeDirty = false;
        }
        return cachedRecipe;
    }

    private void invalidateRecipeCache() {
        recipeDirty = true;
    }

    public String machineKind() {
        return machineProfile().recipeType();
    }

    public MachineProfile machineProfile() {
        final Identifier id = BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock());
        if (id == null) {
            return MachineProfiles.forBlock("unknown");
        }
        return MachineProfiles.forBlock(id.getPath());
    }

    private MachineSlotLayout slotLayout() {
        if (slotLayout == null) {
            slotLayout = MachineSlotLayout.create(machineProfile(), INVENTORY_SIZE);
        }
        return slotLayout;
    }

    public int getProgress() {
        return progress;
    }

    public int getBurnTime() {
        return burnTime;
    }

    public MachineDisplay getMachineDisplay() {
        return machineDisplay;
    }

    public CustomBrewCauldronState getCustomBrewState() {
        return customBrewState;
    }

    private void updateMachineDisplay(final Level level, final BlockPos pos, final BlockState state) {
        final MachineProfile profile = machineProfile();
        final MachineRecipeManager.Diagnostic diagnostic = MachineRecipeManager.INSTANCE.diagnose(
            profile,
            items,
            profile.supportsFluids() ? fluidTank.getFluid() : FluidStack.EMPTY,
            level instanceof ServerLevel serverLevel ? AltarPowerNetwork.available(serverLevel, pos) : 0
        );
        final MachineStatus status;
        if (diagnostic.isEmpty()) {
            status = MachineStatus.EMPTY;
        } else if (diagnostic.recipe().isEmpty()) {
            status = MachineStatus.INVALID;
        } else if (!diagnostic.inputsReady(profile)) {
            status = MachineStatus.INCOMPLETE;
        } else if (profile.requiresExternalHeat() && !isHeated(level, pos)) {
            status = MachineStatus.NO_HEAT;
        } else if (level instanceof ServerLevel serverLevel
            && !diagnostic.recipe().isEmpty()
            && !BodegaBrewingRules.allows(serverLevel, pos, Identifier.parse(diagnostic.recipe()))) {
            status = MachineStatus.NO_FAMILIAR;
        } else if ("brazier".equals(profile.recipeType()) && !brazierIgnited) {
            status = MachineStatus.NO_IGNITION;
        } else {
            final Optional<MachineRecipeManager.Match> recipe = MachineRecipeManager.INSTANCE.byId(Identifier.parse(diagnostic.recipe()));
            final List<ItemStack> outputs = recipe
                .map(MachineRecipeManager.Match::recipe)
                .map(MachineRecipeManager.INSTANCE::createOutputs)
                .map(stacks -> MachineUpgradeRules.enhanceOutputs(
                    stacks,
                    MachineUpgradeRules.around(level, pos, profile)
                ))
                .orElseGet(List::of);
            if (outputs.isEmpty() || outputs.stream().anyMatch(ItemStack::isEmpty) || !canAccept(outputs, profile)) {
                status = MachineStatus.OUTPUT_BLOCKED;
            } else if (profile.hasFuelSlot() && burnTime <= 0
                && level.fuelValues().burnDuration(items.get(profile.fuelSlot())) <= 0) {
                status = MachineStatus.NO_FUEL;
            } else if (diagnostic.recipe().equals(activeRecipe) && progress > 0) {
                status = MachineStatus.PROCESSING;
            } else {
                status = MachineStatus.READY;
            }
        }
        final int percent = diagnostic.processingTime() <= 0
            ? 0
            : Math.clamp((progress * 100 / diagnostic.processingTime()) / 5 * 5, 0, 100);
        final MachineDisplay next = new MachineDisplay(diagnostic, status, percent);
        if (!next.equals(machineDisplay)) {
            machineDisplay = next;
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.warlockery." + machineKind());
    }

    @Override
    public int getContainerSize() {
        return INVENTORY_SIZE;
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
        final MachineProfile profile = machineProfile();
        if (profile.hasFuelSlot() && slot == profile.fuelSlot()) {
            return level != null && level.fuelValues().isFuel(stack);
        }
        return slot < profile.inputSlots();
    }

    @Override
    public int[] getSlotsForFace(final Direction direction) {
        return slotLayout().slotsFor(direction);
    }

    @Override
    public boolean canPlaceItemThroughFace(
        final int slot,
        final ItemStack stack,
        final @Nullable Direction direction
    ) {
        return direction != null && slotLayout().accepts(direction, slot) && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(final int slot, final ItemStack stack, final Direction direction) {
        if (slotLayout().extractsOutput(direction, slot)) {
            return true;
        }
        return slotLayout().extractsFuelRemainder(direction, slot)
            && (level == null || !level.fuelValues().isFuel(stack));
    }

    @Override
    public <T> LazyOptional<T> getCapability(final Capability<T> capability, final @Nullable Direction facing) {
        if (capability == ForgeCapabilities.FLUID_HANDLER && machineProfile().supportsFluids() && !remove) {
            return fluidHandler.cast();
        }
        if (capability == ForgeCapabilities.ITEM_HANDLER && facing != null && !remove) {
            return switch (facing) {
                case UP -> itemHandlers[0].cast();
                case DOWN -> itemHandlers[1].cast();
                default -> itemHandlers[2].cast();
            };
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        java.util.Arrays.stream(itemHandlers).forEach(LazyOptional::invalidate);
        fluidHandler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandlers = createItemHandlers();
        fluidHandler = LazyOptional.of(() -> fluidTank);
    }

    private LazyOptional<? extends IItemHandler>[] createItemHandlers() {
        return SidedInvWrapper.create(this, Direction.UP, Direction.DOWN, Direction.NORTH);
    }

    @Override
    public void setItem(final int slot, final ItemStack stack) {
        super.setItem(slot, stack);
        invalidateRecipeCache();
    }

    @Override
    public ItemStack removeItem(final int slot, final int count) {
        final ItemStack removed = super.removeItem(slot, count);
        if (!removed.isEmpty()) {
            invalidateRecipeCache();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(final int slot) {
        final ItemStack removed = super.removeItemNoUpdate(slot);
        if (!removed.isEmpty()) {
            invalidateRecipeCache();
        }
        return removed;
    }

    @Override
    public void clearContent() {
        super.clearContent();
        invalidateRecipeCache();
    }

    public boolean igniteBrazier() {
        if (!"brazier".equals(machineKind())) {
            return false;
        }
        brazierIgnited = true;
        setChanged();
        if (level != null) {
            updateMachineDisplay(level, worldPosition, getBlockState());
        }
        return true;
    }

    public int extinguishBrazier() {
        if (!"brazier".equals(machineKind())) {
            return 0;
        }
        final MachineProfile profile = machineProfile();
        final int cleared = (int) items.stream().limit(profile.inputSlots()).filter(stack -> !stack.isEmpty()).count();
        java.util.stream.IntStream.range(0, profile.inputSlots()).forEach(slot -> items.set(slot, ItemStack.EMPTY));
        brazierIgnited = false;
        resetProgress();
        invalidateRecipeCache();
        setChanged();
        if (level != null) {
            updateMachineDisplay(level, worldPosition, getBlockState());
        }
        return cleared;
    }

    @Override
    protected AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this, 1);
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        burnTime = input.getIntOr("BurnTime", 0);
        progress = input.getIntOr("Progress", 0);
        activeRecipe = input.getStringOr("ActiveRecipe", "");
        machineDisplay = input.read("MachineDisplay", MachineDisplay.CODEC)
            .orElseGet(() -> readLegacyMachineDisplay(input));
        customBrewState = input.read("CustomBrewState", CustomBrewCauldronState.CODEC)
            .orElse(CustomBrewCauldronState.EMPTY);
        customBrewProgress = input.getIntOr("CustomBrewProgress", 0);
        brazierIgnited = input.getBooleanOr("BrazierIgnited", false);
        fluidTank.readFrom(input.childOrEmpty("FluidTank"));
        invalidateRecipeCache();
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("BurnTime", burnTime);
        output.putInt("Progress", progress);
        output.putString("ActiveRecipe", activeRecipe);
        output.store("MachineDisplay", MachineDisplay.CODEC, machineDisplay);
        output.store("CustomBrewState", CustomBrewCauldronState.CODEC, customBrewState);
        output.putInt("CustomBrewProgress", customBrewProgress);
        output.putBoolean("BrazierIgnited", brazierIgnited);
        if (!fluidTank.isEmpty()) {
            fluidTank.writeTo(output.child("FluidTank"));
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.store("MachineDisplay", MachineDisplay.CODEC, machineDisplay);
        tag.store("CustomBrewState", CustomBrewCauldronState.CODEC, customBrewState);
        return tag;
    }

    private static MachineDisplay readLegacyMachineDisplay(final ValueInput input) {
        final int missingSize = Math.clamp(input.getIntOr("CauldronMissingSize", 0), 0, 6);
        final int wrongSize = Math.clamp(input.getIntOr("CauldronWrongSize", 0), 0, 6);
        final List<MachineRecipeManager.MissingInput> missing = java.util.stream.IntStream.range(0, missingSize)
            .mapToObj(index -> new MachineRecipeManager.MissingInput(
                input.getStringOr("CauldronMissingId" + index, ""),
                input.getIntOr("CauldronMissingCount" + index, 0)
            ))
            .filter(entry -> !entry.ingredient().isBlank() && entry.count() > 0)
            .toList();
        final List<MachineRecipeManager.WrongInput> wrong = java.util.stream.IntStream.range(0, wrongSize)
            .mapToObj(index -> new MachineRecipeManager.WrongInput(
                input.getStringOr("CauldronWrongId" + index, ""),
                input.getIntOr("CauldronWrongCount" + index, 0)
            ))
            .filter(entry -> !entry.item().isBlank() && entry.count() > 0)
            .toList();
        final MachineRecipeManager.Diagnostic diagnostic = new MachineRecipeManager.Diagnostic(
            input.getStringOr("CauldronRecipe", ""),
            input.getStringOr("CauldronOutput", ""),
            input.getIntOr("CauldronTime", 0),
            missing,
            wrong
        );
        return new MachineDisplay(
            diagnostic,
            MachineStatus.fromId(input.getStringOr("CauldronStatus", "empty")),
            input.getIntOr("CauldronProgress", 0)
        );
    }
}
