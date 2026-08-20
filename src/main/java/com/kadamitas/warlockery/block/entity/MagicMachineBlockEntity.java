package com.kadamitas.warlockery.block.entity;

import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.crafting.KettleBrewerContext;
import com.kadamitas.warlockery.crafting.AltarPowerNetwork;
import com.kadamitas.warlockery.crafting.BrazierEffectRuntime;
import com.kadamitas.warlockery.crafting.BrazierEffectRules;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.SpiritWorldMachineRules;
import com.kadamitas.warlockery.crafting.MachineDisplay;
import com.kadamitas.warlockery.crafting.MachineInsertionRules;
import com.kadamitas.warlockery.crafting.MachineInventoryMigration;
import com.kadamitas.warlockery.crafting.MachineProfile;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineSlotLayout;
import com.kadamitas.warlockery.crafting.MachineStatus;
import com.kadamitas.warlockery.crafting.MachineUpgradeRules;
import com.kadamitas.warlockery.crafting.PowerMode;
import com.kadamitas.warlockery.crafting.ArchfiendBrewingRisk;
import com.kadamitas.warlockery.crafting.SilverVatFurnaceObserver;
import com.kadamitas.warlockery.block.MagicMachineBlock;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.ModSounds;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.item.EquipmentSetEffects;
import com.kadamitas.warlockery.menu.MachineMenu;
import com.kadamitas.warlockery.brew.custom.CustomBrewCauldronState;
import com.kadamitas.warlockery.brew.custom.CustomBrewComposer;
import com.kadamitas.warlockery.brew.custom.CustomBrewDefinitionManager;
import com.kadamitas.warlockery.brew.custom.CustomBrewRuntime;
import com.kadamitas.warlockery.brew.BodegaBrewingRules;
import com.kadamitas.warlockery.brew.CauldronChalkCircles;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

public final class MagicMachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    private static final int INVENTORY_SIZE = 9;
    private static final int FLUID_CAPACITY = 4_000;

    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int burnTime;
    private int progress;
    private String activeRecipe = "";
    private long cachedRevision = -1;
    private boolean recipeDirty = true;
    private Optional<MachineRecipeManager.Match> cachedRecipe = Optional.empty();
    private MachineDisplay machineDisplay = MachineDisplay.EMPTY;
    private CustomBrewCauldronState customBrewState = CustomBrewCauldronState.EMPTY;
    private CauldronChalkCircles.State cauldronChalkCircles = CauldronChalkCircles.State.EMPTY;
    private int customBrewProgress;
    private boolean brazierIgnited;
    private boolean brazierRedstonePowered;
    private int brazierBurnExtension;
    private final SilverVatFurnaceObserver silverVatFurnaceObserver = new SilverVatFurnaceObserver();
    private int pendingSilverDeposits;
    private List<ItemStack> legacyMachineOverflow = List.of();
    private boolean pendingInventoryMigrationSave;
    private KettleBrewerContext kettleBrewer = KettleBrewerContext.EMPTY;
    private MachineSlotLayout slotLayout;
    private final FluidStacksResourceHandler fluidTank = new FluidStacksResourceHandler(1, FLUID_CAPACITY) {
        @Override
        protected void onContentsChanged(final int index, final FluidStack previousContents) {
            invalidateRecipeCache();
            setChanged();
        }
    };
    private final ResourceHandler<FluidResource> legacyFluidHandler = new DrainOnlyFluidHandler(fluidTank);
    private long menuSnapshotTick = Long.MIN_VALUE;
    private MachineMenuSnapshot menuSnapshot;

    public MagicMachineBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.MAGIC_MACHINE.get(), pos, state);
    }

    public static void serverTick(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final MagicMachineBlockEntity machine
    ) {
        machine.persistCompletedInventoryMigration();
        if (machine.burnTime > 0) {
            machine.burnTime--;
        }

        final MachineProfile profile = machine.machineProfile();
        if ("silvervat".equals(profile.recipeType()) && level instanceof ServerLevel serverLevel) {
            machine.tickSilverVatFurnaces(serverLevel, pos, profile);
        }
        if ("cauldron".equals(profile.recipeType())) {
            machine.updateCauldronChalkCircles(level, pos, state);
        }
        if ("brazier".equals(profile.recipeType())) {
            final boolean redstonePowered = level.hasNeighborSignal(pos);
            if (machine.brazierRedstonePowered != redstonePowered) {
                if (BrazierEffectRules.isRisingEdge(
                    machine.brazierRedstonePowered,
                    redstonePowered
                )) {
                    machine.igniteBrazier();
                }
                machine.brazierRedstonePowered = redstonePowered;
                machine.setChanged();
            }
            if (!BrazierEffectRules.canContinueBurn(
                machine.brazierIgnited,
                machine.hasBrazierAsh(profile)
            )) {
                if (machine.brazierIgnited) {
                    machine.brazierIgnited = false;
                    machine.setChanged();
                }
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

        final var match = machine.findRecipe(profile);
        if (match.isEmpty()) {
            if ("brazier".equals(profile.recipeType()) && machine.brazierIgnited) {
                machine.extinguishBrazier();
            }
            machine.resetProgress();
            setLit(level, pos, state, machine.burnTime > 0);
            machine.updateMachineDisplay(level, pos, state);
            return;
        }

        final MachineRecipeDefinition recipe = match.get().recipe();
        if (level instanceof ServerLevel serverLevel
            && !BodegaBrewingRules.allows(
                serverLevel,
                pos,
                match.get().id(),
                machine.kettleBrewerId(level.getGameTime())
            )) {
            machine.resetProgress();
            setLit(level, pos, state, false);
            machine.updateMachineDisplay(level, pos, state);
            return;
        }
        if (!SpiritWorldMachineRules.allows(match.get().id(), level.dimension().identifier())) {
            machine.resetProgress();
            setLit(level, pos, state, false);
            machine.updateMachineDisplay(level, pos, state);
            return;
        }
        final MachineUpgradeRules.Upgrade upgrade = MachineUpgradeRules.around(level, pos, profile);
        final List<ItemStack> upgradedOutputs = MachineUpgradeRules.enhanceOutputs(
            MachineRecipeManager.INSTANCE.createOutputs(recipe), upgrade
        );
        final List<ItemStack> outputs;
        if (level instanceof ServerLevel serverLevel) {
            outputs = "kettle".equals(profile.recipeType())
                ? EquipmentSetEffects.enhanceMachineOutputs(
                    serverLevel,
                    machine.kettleBrewer(serverLevel),
                    profile.recipeType(),
                    upgradedOutputs
                )
                : EquipmentSetEffects.enhanceNearbyMachineOutputs(
                    serverLevel,
                    pos,
                    profile.recipeType(),
                    upgradedOutputs
                );
        } else {
            outputs = upgradedOutputs;
        }
        if (outputs.stream().anyMatch(ItemStack::isEmpty) || !machine.canAccept(outputs, profile)) {
            machine.resetProgress();
            setLit(level, pos, state, machine.burnTime > 0);
            machine.updateMachineDisplay(level, pos, state);
            return;
        }

        if (level instanceof ServerLevel serverLevel
            && AltarPowerNetwork.available(serverLevel, pos)
                < recipe.powerMode().requiredAvailablePower(recipe.altarPower())) {
            if (recipe.powerMode() != PowerMode.CONTINUOUS) {
                machine.resetProgress();
            }
            setLit(level, pos, state, false);
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
            machine.brazierBurnExtension = 0;
        }

        if (machine.progress == 0) {
            machine.beginKettleCycle(level.getGameTime());
            level.playSound(null, pos, ModSounds.MACHINE_START.get(), SoundSource.BLOCKS, 0.55F, 1.0F);
        }
        final int previousProgress = machine.progress;
        final int processingTarget = machine.processingTarget(recipe);
        final int nextProgress = Math.min(
            processingTarget,
            (int) Math.min(Integer.MAX_VALUE, (long) previousProgress + upgrade.progressPerTick())
        );
        final int continuousPower = recipe.powerMode().powerForAdvance(
            recipe.altarPower(),
            recipe.processingTime(),
            previousProgress,
            nextProgress
        );
        if (level instanceof ServerLevel serverLevel
            && !AltarPowerNetwork.consume(serverLevel, pos, continuousPower)) {
            setLit(level, pos, state, false);
            machine.invalidateRecipeCache();
            machine.updateMachineDisplay(level, pos, state);
            return;
        }
        machine.progress = nextProgress;
        if (level instanceof ServerLevel serverLevel
            && "brazier".equals(profile.recipeType())
            && nextProgress < processingTarget) {
            final BrazierEffectRuntime.Result effect = BrazierEffectRuntime.applyDuringBurn(
                serverLevel,
                pos,
                match.get().id(),
                previousProgress,
                nextProgress
            );
            machine.extendBrazierBurn(effect.cropsDrained());
        }
        setLit(level, pos, state, true);

        if (machine.progress >= processingTarget) {
            if (level instanceof ServerLevel serverLevel
                && !AltarPowerNetwork.consume(
                    serverLevel,
                    pos,
                    recipe.powerMode().completionCost(recipe.altarPower())
                )) {
                machine.resetProgress();
                machine.invalidateRecipeCache();
                machine.updateMachineDisplay(level, pos, state);
                return;
            }
            MachineRecipeManager.INSTANCE.consumeInputs(recipe, machine.items, profile.inputSlots());
            MachineRecipeManager.INSTANCE.consumeFluid(recipe, machine.fluidTank);
            if (!"brazier".equals(profile.recipeType())) {
                machine.insertOutputs(outputs, profile);
            }
            if (level instanceof ServerLevel serverLevel && "cauldron".equals(profile.recipeType())) {
                ArchfiendBrewingRisk.apply(serverLevel, pos, machine.cauldronChalkCircles);
            }
            if (level instanceof ServerLevel serverLevel && "brazier".equals(profile.recipeType())) {
                BrazierEffectRuntime.apply(serverLevel, pos, match.get().id());
                machine.brazierIgnited = false;
            }
            machine.progress = 0;
            machine.activeRecipe = "";
            machine.brazierBurnExtension = 0;
            machine.recipeDirty = true;
            machine.clearKettleCycle();
            level.playSound(null, pos, ModSounds.MACHINE_COMPLETE.get(), SoundSource.BLOCKS, 0.75F, 1.0F);
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
            fluidStack(),
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
            final ItemStack baseOutput = next.formula()
                .map(formula -> CauldronChalkCircles.influence(formula, cauldronChalkCircles))
                .map(CustomBrewRuntime::createOutput)
                .orElse(ItemStack.EMPTY);
            final ItemStack output = EquipmentSetEffects.enhanceNearbyMachineOutputs(
                serverLevel, pos, profile.recipeType(), List.of(baseOutput)
            ).stream().findFirst().orElse(ItemStack.EMPTY);
            if (output.isEmpty() || !items.get(profile.outputStart()).isEmpty()) {
                customBrewProgress = 0;
                updateCustomBrewState(level, pos, state, next);
                return true;
            }
            items.stream().limit(profile.inputSlots()).filter(stack -> !stack.isEmpty()).forEach(stack -> stack.shrink(1));
            extractFluid(CustomBrewComposer.WATER_REQUIRED);
            items.set(profile.outputStart(), output);
            ArchfiendBrewingRisk.apply(serverLevel, pos, cauldronChalkCircles);
            customBrewProgress = 0;
            next = CustomBrewCauldronState.EMPTY;
            recipeDirty = true;
            level.playSound(null, pos, ModSounds.MACHINE_COMPLETE.get(), SoundSource.BLOCKS, 0.8F, 1.1F);
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
        final int duration = burnDuration(level, fuel);
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

    private void tickSilverVatFurnaces(
        final ServerLevel level,
        final BlockPos vatPos,
        final MachineProfile profile
    ) {
        final List<SilverVatFurnaceObserver.FurnaceCycle> furnaces = Stream.of(Direction.values())
            .map(vatPos::relative)
            .map(pos -> furnaceCycle(level, pos))
            .flatMap(Optional::stream)
            .toList();
        final Map<Long, Integer> previousProgress = silverVatFurnaceObserver.snapshot();
        final int generated = silverVatFurnaceObserver.observe(furnaces);
        pendingSilverDeposits = (int) Math.min(Integer.MAX_VALUE, (long) pendingSilverDeposits + generated);
        final int inserted = insertSilverDeposits(pendingSilverDeposits, profile);
        if (inserted > 0) {
            pendingSilverDeposits -= inserted;
            invalidateRecipeCache();
        }
        if (generated > 0 || inserted > 0 || !previousProgress.equals(silverVatFurnaceObserver.snapshot())) {
            setChanged();
        }
    }

    private static Optional<SilverVatFurnaceObserver.FurnaceCycle> furnaceCycle(
        final ServerLevel level,
        final BlockPos furnacePos
    ) {
        if (!(level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return Optional.empty();
        }
        final ItemStack input = furnace.getItem(0);
        final SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        final Optional<? extends AbstractCookingRecipe> recipe = furnace instanceof BlastFurnaceBlockEntity
            ? level.recipeAccess().getRecipeFor(RecipeType.BLASTING, recipeInput, level).map(holder -> holder.value())
            : level.recipeAccess().getRecipeFor(RecipeType.SMELTING, recipeInput, level).map(holder -> holder.value());
        final FurnaceProcess fallback = new FurnaceProcess(
            ItemStack.EMPTY,
            furnace instanceof BlastFurnaceBlockEntity ? 100 : 200
        );
        final FurnaceProcess process = recipe.map(value -> new FurnaceProcess(
            value.assemble(recipeInput),
            value.cookingTime()
        )).orElse(fallback);
        final boolean goldOre = input.is(Tags.Items.ORES_GOLD);
        final boolean goldResult = process.result().is(Tags.Items.INGOTS_GOLD) || process.result().is(Items.GOLD_INGOT);
        final ItemStack output = furnace.getItem(2);
        final boolean outputAvailable = output.isEmpty()
            || ItemStack.isSameItemSameComponents(output, process.result())
                && output.getCount() + process.result().getCount() <= output.getMaxStackSize();
        final BlockState furnaceState = level.getBlockState(furnacePos);
        final boolean lit = furnaceState.hasProperty(BlockStateProperties.LIT)
            && furnaceState.getValue(BlockStateProperties.LIT);
        return Optional.of(new SilverVatFurnaceObserver.FurnaceCycle(
            furnacePos.asLong(),
            lit && goldOre && goldResult && outputAvailable,
            input.isEmpty(),
            process.cookingTime()
        ));
    }

    private int insertSilverDeposits(final int deposits, final MachineProfile profile) {
        if (deposits <= 0) {
            return 0;
        }
        final var silverDeposit = ModItems.ALL.get("ingredient_silverdust").get();
        int remaining = deposits;
        for (int slot = profile.outputStart(); slot < INVENTORY_SIZE && remaining > 0; slot++) {
            final ItemStack existing = items.get(slot);
            if (!existing.isEmpty() && !existing.is(silverDeposit)) {
                continue;
            }
            final int capacity = existing.isEmpty() ? silverDeposit.getDefaultMaxStackSize() : existing.getMaxStackSize();
            final int accepted = Math.min(remaining, capacity - existing.getCount());
            if (accepted <= 0) {
                continue;
            }
            if (existing.isEmpty()) {
                items.set(slot, new ItemStack(silverDeposit, accepted));
            } else {
                existing.grow(accepted);
            }
            remaining -= accepted;
        }
        return deposits - remaining;
    }

    private record FurnaceProcess(ItemStack result, int cookingTime) {
    }

    private boolean canAccept(final List<ItemStack> outputs, final MachineProfile profile) {
        final int outputStart = profile.outputStart();
        if (outputs.size() > profile.outputSlots()) {
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
        if (progress != 0 || !activeRecipe.isEmpty() || brazierBurnExtension != 0) {
            progress = 0;
            activeRecipe = "";
            brazierBurnExtension = 0;
            clearKettleCycle();
            setChanged();
        }
    }

    private Optional<MachineRecipeManager.Match> findRecipe(final MachineProfile profile) {
        final MachineRecipeManager manager = MachineRecipeManager.INSTANCE;
        if (recipeDirty || cachedRevision != manager.revision()) {
            cachedRecipe = manager.find(
                profile,
                items,
                profile.supportsFluids() ? fluidStack() : FluidStack.EMPTY,
                getAvailableAltarPower()
            );
            cachedRevision = manager.revision();
            recipeDirty = false;
        }
        return cachedRecipe;
    }

    private void invalidateRecipeCache() {
        recipeDirty = true;
    }

    private FluidStack fluidStack() {
        return FluidUtil.getStack(fluidTank, 0);
    }

    private void extractFluid(final int amount) {
        final FluidResource resource = fluidTank.getResource(0);
        if (resource.isEmpty()) {
            return;
        }
        try (var transaction = Transaction.openRoot()) {
            if (fluidTank.extract(0, resource, amount, transaction) == amount) {
                transaction.commit();
            }
        }
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

    public int getFluidAmount() {
        return fluidStack().getAmount();
    }

    public int getAvailableAltarPower() {
        return level instanceof ServerLevel serverLevel
            ? AltarPowerNetwork.available(serverLevel, worldPosition)
            : 0;
    }

    public int getRequiredAltarPower() {
        return powerRecipe()
            .map(recipe -> recipe.powerMode().requiredAvailablePower(recipe.altarPower()))
            .orElse(0);
    }

    public int getTotalAltarPower() {
        return powerRecipe().map(MachineRecipeDefinition::altarPower).orElse(0);
    }

    public int getAltarMillipowerPerTick() {
        return powerRecipe()
            .map(recipe -> recipe.powerMode().millipowerPerTick(recipe.altarPower(), recipe.processingTime()))
            .orElse(0);
    }

    public int getPowerModeOrdinal() {
        return powerRecipe().map(MachineRecipeDefinition::powerMode).orElse(PowerMode.NONE).ordinal();
    }

    public MachineMenuSnapshot getMachineMenuSnapshot() {
        final long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (menuSnapshot != null && menuSnapshotTick == gameTime) {
            return menuSnapshot;
        }
        final Optional<MachineRecipeDefinition> recipe = powerRecipe();
        menuSnapshot = new MachineMenuSnapshot(
            machineDisplay.progressPercent(),
            machineDisplay.status().ordinal(),
            getFluidAmount(),
            getAvailableAltarPower(),
            recipe.map(value -> value.powerMode().requiredAvailablePower(value.altarPower())).orElse(0),
            recipe.map(MachineRecipeDefinition::altarPower).orElse(0),
            recipe.map(value -> value.powerMode().millipowerPerTick(value.altarPower(), value.processingTime()))
                .orElse(0),
            recipe.map(MachineRecipeDefinition::powerMode).orElse(PowerMode.NONE).ordinal()
        );
        menuSnapshotTick = gameTime;
        return menuSnapshot;
    }

    private Optional<MachineRecipeDefinition> powerRecipe() {
        final String recipe = activeRecipe.isEmpty() ? machineDisplay.diagnostic().recipe() : activeRecipe;
        return Optional.ofNullable(Identifier.tryParse(recipe))
            .flatMap(MachineRecipeManager.INSTANCE::byId)
            .map(MachineRecipeManager.Match::recipe);
    }

    public MachineDisplay getMachineDisplay() {
        return machineDisplay;
    }

    public Optional<UUID> kettleBrewerId(final long gameTime) {
        return "kettle".equals(machineKind()) ? kettleBrewer.brewer(gameTime) : Optional.empty();
    }

    public void claimKettleBrewer(final Player player) {
        if ("kettle".equals(machineKind()) && level != null && progress == 0 && activeRecipe.isEmpty()) {
            kettleBrewer = kettleBrewer.claim(player.getUUID(), level.getGameTime());
        }
    }

    public @Nullable ServerPlayer kettleBrewer(final ServerLevel level) {
        return kettleBrewerId(level.getGameTime())
            .map(level.getServer().getPlayerList()::getPlayer)
            .orElse(null);
    }

    public void beginKettleCycle(final long gameTime) {
        if ("kettle".equals(machineKind())) {
            kettleBrewer = kettleBrewer.begin(gameTime);
        }
    }

    public void clearKettleCycle() {
        if (!kettleBrewer.equals(KettleBrewerContext.EMPTY)) {
            kettleBrewer = kettleBrewer.clear();
            setChanged();
        }
    }

    public CustomBrewCauldronState getCustomBrewState() {
        return customBrewState;
    }

    public CauldronChalkCircles.State getCauldronChalkCircles() {
        return cauldronChalkCircles;
    }

    private void updateCauldronChalkCircles(
        final Level level,
        final BlockPos pos,
        final BlockState state
    ) {
        final CauldronChalkCircles.State next = CauldronChalkCircles.inspect(level, pos);
        if (!next.equals(cauldronChalkCircles)) {
            cauldronChalkCircles = next;
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void updateMachineDisplay(final Level level, final BlockPos pos, final BlockState state) {
        final MachineProfile profile = machineProfile();
        final MachineRecipeManager.Diagnostic diagnostic = MachineRecipeManager.INSTANCE.diagnose(
            profile,
            items,
            profile.supportsFluids() ? fluidStack() : FluidStack.EMPTY,
            level instanceof ServerLevel serverLevel ? AltarPowerNetwork.available(serverLevel, pos) : 0
        );
        final boolean missingAltarPower = diagnostic.missing().stream()
            .anyMatch(missing -> "warlockery:altar_power".equals(missing.ingredient()));
        final boolean missingSomethingElse = diagnostic.missing().stream()
            .anyMatch(missing -> !"warlockery:altar_power".equals(missing.ingredient()))
            || profile.rejectsUnexpectedInputs() && !diagnostic.wrong().isEmpty();
        final MachineStatus status;
        if (diagnostic.isEmpty()) {
            status = MachineStatus.EMPTY;
        } else if (diagnostic.recipe().isEmpty()) {
            status = MachineStatus.INVALID;
        } else if (missingAltarPower && !missingSomethingElse) {
            status = MachineStatus.NO_ALTAR_POWER;
        } else if (!diagnostic.inputsReady(profile)) {
            status = MachineStatus.INCOMPLETE;
        } else if (profile.requiresExternalHeat() && !isHeated(level, pos)) {
            status = MachineStatus.NO_HEAT;
        } else if (level instanceof ServerLevel serverLevel
            && !diagnostic.recipe().isEmpty()
            && !BodegaBrewingRules.allows(
                serverLevel,
                pos,
                Identifier.parse(diagnostic.recipe()),
                kettleBrewerId(level.getGameTime())
            )) {
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
                && burnDuration(level, items.get(profile.fuelSlot())) <= 0) {
                status = MachineStatus.NO_FUEL;
            } else if (diagnostic.recipe().equals(activeRecipe) && progress > 0) {
                status = MachineStatus.PROCESSING;
            } else {
                status = MachineStatus.READY;
            }
        }
        final int displayProcessingTime = activeRecipe.equals(diagnostic.recipe())
            ? powerRecipe().map(this::processingTarget).orElse(diagnostic.processingTime())
            : diagnostic.processingTime();
        final int percent = displayProcessingTime <= 0
            ? 0
            : Math.clamp((int) ((long) progress * 100L / displayProcessingTime) / 5 * 5, 0, 100);
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
        return MachineInsertionRules.accepts(
            profile,
            slot,
            stack,
            level == null || !level.isClientSide(),
            candidate -> MachineRecipeManager.INSTANCE.acceptsInput(profile, slot, candidate),
            candidate -> level != null && level.fuelValues().isFuel(candidate)
        );
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
            && (level == null || burnDuration(level, stack) <= 0);
    }

    private static int burnDuration(final Level level, final ItemStack stack) {
        return stack.getBurnTime(RecipeType.SMELTING, level.fuelValues());
    }

    public static void registerCapabilities(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            ModBlockEntities.MAGIC_MACHINE.get(),
            WorldlyContainerWrapper::new
        );
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            ModBlockEntities.MAGIC_MACHINE.get(),
            (machine, side) -> {
                if (machine.machineProfile().supportsFluids()) {
                    return machine.fluidTank;
                }
                return ResourceHandlerUtil.isEmpty(machine.fluidTank) ? null : machine.legacyFluidHandler;
            }
        );
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
        final MachineProfile profile = machineProfile();
        final boolean hasIngredient = items.stream().limit(profile.inputSlots()).anyMatch(stack -> !stack.isEmpty());
        final ItemStack ash = new ItemStack(ModItems.ALL.get("ingredient_ash_wood").get());
        final ItemStack output = items.get(profile.outputStart());
        final boolean outputAcceptsAsh = output.isEmpty()
            || ItemStack.isSameItemSameComponents(output, ash) && output.getCount() == 1;
        if (!BrazierEffectRules.canIgnite(
            hasIngredient,
            outputAcceptsAsh,
            brazierIgnited
        )) {
            return false;
        }
        if (output.isEmpty()) {
            items.set(profile.outputStart(), ash);
        }
        brazierIgnited = true;
        invalidateRecipeCache();
        setChanged();
        if (level != null) {
            updateMachineDisplay(level, worldPosition, getBlockState());
        }
        return true;
    }

    private boolean hasBrazierAsh(final MachineProfile profile) {
        final ItemStack output = items.get(profile.outputStart());
        return output.is(ModItems.ALL.get("ingredient_ash_wood").get()) && !output.isEmpty();
    }

    public int extinguishBrazier() {
        if (!"brazier".equals(machineKind())) {
            return 0;
        }
        final MachineProfile profile = machineProfile();
        final boolean hasContents = brazierIgnited || items.stream().anyMatch(stack -> !stack.isEmpty());
        if (!hasContents) {
            return -1;
        }
        final int cleared = (int) items.stream().filter(stack -> !stack.isEmpty()).count();
        java.util.stream.IntStream.range(0, getContainerSize()).forEach(slot -> items.set(slot, ItemStack.EMPTY));
        brazierIgnited = false;
        brazierBurnExtension = 0;
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
        recoverLegacyOverflow(inventory.player);
        claimKettleBrewer(inventory.player);
        return new MachineMenu(containerId, inventory, this, machineKind());
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        final MachineInventoryMigration.Result migration = MachineInventoryMigration.migrate(
            machineKind(),
            input.getIntOr("MachineInventoryVersion", 0),
            items,
            (slot, stack) -> MachineRecipeManager.INSTANCE.acceptsInput(machineProfile(), slot, stack)
        );
        items = migration.inventory();
        pendingInventoryMigrationSave = migration.migrated();
        legacyMachineOverflow = Stream.concat(
            input.read("LegacyMachineOverflow", ItemStack.CODEC.listOf()).orElse(List.of()).stream(),
            migration.overflow().stream()
        ).map(ItemStack::copy).toList();
        burnTime = input.getIntOr("BurnTime", 0);
        progress = input.getIntOr("Progress", 0);
        activeRecipe = input.getStringOr("ActiveRecipe", "");
        brazierBurnExtension = Math.max(0, input.getIntOr("BrazierBurnExtension", 0));
        if (migration.migrated()) {
            burnTime = 0;
            progress = 0;
            activeRecipe = "";
            brazierBurnExtension = 0;
        }
        machineDisplay = input.read("MachineDisplay", MachineDisplay.CODEC)
            .orElseGet(() -> readLegacyMachineDisplay(input));
        customBrewState = input.read("CustomBrewState", CustomBrewCauldronState.CODEC)
            .orElse(CustomBrewCauldronState.EMPTY);
        cauldronChalkCircles = input.read("CauldronChalkCircles", CauldronChalkCircles.State.CODEC)
            .orElse(CauldronChalkCircles.State.EMPTY);
        customBrewProgress = input.getIntOr("CustomBrewProgress", 0);
        brazierIgnited = BrazierEffectRules.restoreIgnitionAfterMigration(
            input.getBooleanOr("BrazierIgnited", false),
            migration.migrated()
        );
        brazierRedstonePowered = input.getBooleanOr("BrazierRedstonePowered", false);
        fluidTank.deserialize(input.childOrEmpty("FluidTank"));
        if (ResourceHandlerUtil.isEmpty(fluidTank)) {
            input.read("FluidTank", FluidStack.CODEC).ifPresent(stack ->
                fluidTank.set(0, FluidResource.of(stack), Math.min(stack.getAmount(), FLUID_CAPACITY))
            );
        }
        pendingSilverDeposits = input.getIntOr("PendingSilverDeposits", 0);
        kettleBrewer = KettleBrewerContext.restored(input.read("KettleBrewer", UUIDUtil.CODEC));
        silverVatFurnaceObserver.restore(input.childrenListOrEmpty("SilverVatFurnaces").stream().collect(
            java.util.stream.Collectors.toMap(
                entry -> entry.getLongOr("Position", 0L),
                entry -> entry.getIntOr("Progress", 0),
                Math::max
            )
        ));
        invalidateRecipeCache();
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("MachineInventoryVersion", MachineInventoryMigration.CURRENT_VERSION);
        output.putInt("BurnTime", burnTime);
        output.putInt("Progress", progress);
        output.putString("ActiveRecipe", activeRecipe);
        output.putInt("BrazierBurnExtension", brazierBurnExtension);
        if (!legacyMachineOverflow.isEmpty()) {
            output.store("LegacyMachineOverflow", ItemStack.CODEC.listOf(), legacyMachineOverflow);
        }
        output.store("MachineDisplay", MachineDisplay.CODEC, machineDisplay);
        output.store("CustomBrewState", CustomBrewCauldronState.CODEC, customBrewState);
        output.store("CauldronChalkCircles", CauldronChalkCircles.State.CODEC, cauldronChalkCircles);
        output.putInt("CustomBrewProgress", customBrewProgress);
        output.putBoolean("BrazierIgnited", brazierIgnited);
        output.putBoolean("BrazierRedstonePowered", brazierRedstonePowered);
        output.putInt("PendingSilverDeposits", pendingSilverDeposits);
        kettleBrewer.activeBrewer().ifPresent(brewer -> output.store("KettleBrewer", UUIDUtil.CODEC, brewer));
        final ValueOutput.ValueOutputList observedFurnaces = output.childrenList("SilverVatFurnaces");
        silverVatFurnaceObserver.snapshot().forEach((position, observedProgress) -> {
            final ValueOutput observed = observedFurnaces.addChild();
            observed.putLong("Position", position);
            observed.putInt("Progress", observedProgress);
        });
        if (!ResourceHandlerUtil.isEmpty(fluidTank)) {
            fluidTank.serialize(output.child("FluidTank"));
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
        tag.store("CauldronChalkCircles", CauldronChalkCircles.State.CODEC, cauldronChalkCircles);
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

    private int processingTarget(final MachineRecipeDefinition recipe) {
        return (int) Math.min(
            Integer.MAX_VALUE,
            (long) recipe.processingTime() + Math.max(0, brazierBurnExtension)
        );
    }

    private void extendBrazierBurn(final int cropsDrained) {
        if (cropsDrained <= 0) {
            return;
        }
        final long extension = (long) brazierBurnExtension + cropsDrained * 800L;
        brazierBurnExtension = (int) Math.min(Integer.MAX_VALUE, extension);
        setChanged();
    }

    private void recoverLegacyOverflow(final Player player) {
        if (legacyMachineOverflow.isEmpty()) {
            return;
        }
        final List<ItemStack> recovered = legacyMachineOverflow;
        legacyMachineOverflow = List.of();
        setChanged();
        recovered.stream().map(ItemStack::copy).forEach(stack -> {
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        });
    }

    public void dropLegacyOverflow(final Level level, final BlockPos pos) {
        if (legacyMachineOverflow.isEmpty()) {
            return;
        }
        legacyMachineOverflow.forEach(stack -> Containers.dropItemStack(
            level,
            pos.getX() + 0.5,
            pos.getY() + 1.0,
            pos.getZ() + 0.5,
            stack.copy()
        ));
        legacyMachineOverflow = List.of();
        setChanged();
    }

    private void persistCompletedInventoryMigration() {
        if (!pendingInventoryMigrationSave) {
            return;
        }
        pendingInventoryMigrationSave = false;
        setChanged();
    }

    public record MachineMenuSnapshot(
        int progressPercent,
        int statusOrdinal,
        int fluidAmount,
        int availableAltarPower,
        int requiredAltarPower,
        int totalAltarPower,
        int altarMillipowerPerTick,
        int powerModeOrdinal
    ) {
    }

    private static final class DrainOnlyFluidHandler implements ResourceHandler<FluidResource> {
        private final ResourceHandler<FluidResource> delegate;

        private DrainOnlyFluidHandler(final ResourceHandler<FluidResource> delegate) {
            this.delegate = delegate;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public FluidResource getResource(final int tank) {
            return delegate.getResource(tank);
        }

        @Override
        public long getAmountAsLong(final int tank) {
            return delegate.getAmountAsLong(tank);
        }

        @Override
        public long getCapacityAsLong(final int tank, final FluidResource resource) {
            return delegate.getCapacityAsLong(tank, resource);
        }

        @Override
        public boolean isValid(final int tank, final FluidResource resource) {
            return false;
        }

        @Override
        public int insert(
            final int tank,
            final FluidResource resource,
            final int amount,
            final TransactionContext transaction
        ) {
            return 0;
        }

        @Override
        public int extract(
            final int tank,
            final FluidResource resource,
            final int amount,
            final TransactionContext transaction
        ) {
            return delegate.extract(tank, resource, amount, transaction);
        }
    }
}
