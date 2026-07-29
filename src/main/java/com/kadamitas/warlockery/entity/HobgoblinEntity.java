package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ContainerOrHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

public class HobgoblinEntity extends Villager implements ArcaneCreature {
    private final CreatureKind kind;
    private final CreatureBehavior behavior;
    private KoboldProfession koboldProfession = KoboldProfession.PROSPECTOR;
    private int prospectingCooldown;

    public HobgoblinEntity(final EntityType<? extends Villager> type, final Level level, final CreatureKind kind) {
        super(type, level);
        this.kind = kind;
        this.behavior = CreatureBehaviorFactory.create(kind);
        if (isPatronBoss()) {
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this,
                Player.class,
                true,
                (target, serverLevel) -> behavior.canAttack(this, target)
            ));
        }
    }

    @Override
    public CreatureKind creatureKind() {
        return kind;
    }

    public KoboldProfession koboldProfession() {
        return koboldProfession;
    }

    public void assignProfessionFromVillage() {
        this.koboldProfession = Arrays.stream(KoboldProfession.values())
            .filter(role -> this.level().getBlockStates(this.getBoundingBox().inflate(10)).anyMatch(state -> state.is(role.workstation)))
            .min(Comparator.comparingInt(Enum::ordinal))
            .orElse(KoboldProfession.PROSPECTOR);
        this.setCustomName(Component.translatable("entity.warlockery.hobgoblin.profession." + koboldProfession.id));
        this.getOffers().clear();
    }

    @Override
    protected void updateTrades(final ServerLevel level) {
        final var emerald = new ItemCost(Items.EMERALD, 1);
        switch (koboldProfession) {
            case MINER -> {
                this.getOffers().add(new MerchantOffer(new ItemCost(Items.COAL, 12), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
                this.getOffers().add(new MerchantOffer(emerald, new ItemStack(ModItems.ALL.get("raw_delvealloy").get(), 2), 8, 8, 0.12F));
                this.getOffers().add(new MerchantOffer(
                    new ItemCost(ModItems.ALL.get("ingredient_delvealloydust").get(), 8),
                    new ItemStack(Items.EMERALD),
                    12,
                    10,
                    0.08F
                ));
            }
            case SMITH -> {
                this.getOffers().add(new MerchantOffer(new ItemCost(ModItems.ALL.get("raw_delvealloy").get(), 4), new ItemStack(Items.EMERALD), 12, 5, 0.08F));
                this.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 18), new ItemStack(ModItems.ALL.get("delvealloypickaxe").get()), 2, 20, 0.2F));
            }
            case SHAMAN -> {
                this.getOffers().add(new MerchantOffer(new ItemCost(Items.REDSTONE, 8), new ItemStack(ModItems.ALL.get("ingredient_whiff_of_magic").get()), 12, 8, 0.08F));
                this.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(ModItems.ALL.get("ingredient_attuned_stone").get()), 8, 12, 0.12F));
            }
            case PROSPECTOR -> {
                this.getOffers().add(new MerchantOffer(new ItemCost(ModItems.ALL.get("raw_silver").get(), 5), new ItemStack(Items.EMERALD), 12, 5, 0.08F));
                this.getOffers().add(new MerchantOffer(
                    new ItemCost(ModItems.ALL.get("ingredient_delvealloydust").get(), 9),
                    new ItemStack(ModItems.ALL.get("ingredient_delvealloynugget").get()),
                    12,
                    12,
                    0.12F
                ));
                this.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(ModItems.ALL.get("ingredient_delvealloynugget").get(), 4), 12, 8, 0.12F));
            }
        }
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        behavior.tick(this, level);
        if (tickCount % 20 == 0) {
            performServitudeWork(level);
        }
        if (koboldProfession != KoboldProfession.MINER || isTrading() || isNoAi()) {
            return;
        }
        if (!getMainHandItem().is(WarlockeryTags.Items.HOBGOBLIN_MINING_TOOLS)) {
            prospectingCooldown = 100;
            return;
        }
        if (prospectingCooldown > 0) {
            prospectingCooldown--;
            return;
        }
        prospect(level);
    }

    public boolean prospect(final ServerLevel level) {
        final ItemStack tool = getMainHandItem();
        if (!tool.is(WarlockeryTags.Items.HOBGOBLIN_MINING_TOOLS)) {
            prospectingCooldown = 100;
            return false;
        }
        final HobgoblinMiningRules.MiningProfile profile = HobgoblinMiningRules.profile(
            tool.is(WarlockeryTags.Items.ENHANCED_HOBGOBLIN_MINING_TOOLS)
        );
        final Optional<BlockPos> target = BlockPos.betweenClosedStream(
                blockPosition().offset(-5, -2, -5),
                blockPosition().offset(5, 2, 5)
            )
            .filter(pos -> isMineable(level, pos, tool))
            .min(Comparator.comparingDouble(pos -> distanceToSqr(Vec3.atCenterOf(pos))));
        if (target.isEmpty()) {
            prospectingCooldown = 200;
            return false;
        }
        final BlockPos position = target.orElseThrow();
        if (distanceToSqr(Vec3.atCenterOf(position)) > 9.0) {
            getNavigation().moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.8);
            prospectingCooldown = 40;
            return false;
        }
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            prospectingCooldown = 200;
            return false;
        }
        final BlockState state = level.getBlockState(position);
        final BlockEntity blockEntity = level.getBlockEntity(position);
        final List<ItemStack> drops = Block.getDrops(state, level, position, blockEntity, this, tool);
        if (!level.destroyBlock(position, false, this)) {
            prospectingCooldown = 200;
            return false;
        }
        swing(InteractionHand.MAIN_HAND);
        dropMinedResources(level, position, state, drops, tool, profile);
        prospectingCooldown = profile.cooldownTicks();
        return true;
    }

    @Override
    public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final InteractionResult behaviorResult = behavior.interact(this, player, hand);
        if (behaviorResult != InteractionResult.PASS) {
            return behaviorResult;
        }
        final ItemStack supplied = player.getItemInHand(hand);
        if (!supplied.is(WarlockeryTags.Items.HOBGOBLIN_MINING_TOOLS)) {
            return super.mobInteract(player, hand);
        }
        if (level() instanceof ServerLevel serverLevel) {
            equipMiningTool(serverLevel, player, supplied);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean wantsToPickUp(final ServerLevel level, final ItemStack stack) {
        return CreatureBehaviorState.owner(this).isPresent()
            && stack.is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES)
            || super.wantsToPickUp(level, stack);
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return behavior.canAttack(this, target) && super.canAttack(target);
    }

    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        final boolean hurt = super.doHurtTarget(level, target);
        if (hurt) {
            behavior.afterAttack(this, level, target);
        }
        return hurt;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            behavior.afterHurt(this, level, source, amount);
        }
        return hurt;
    }

    private void performServitudeWork(final ServerLevel level) {
        if (!HobgoblinWorkRules.canWork(
            CreatureBehaviorState.owner(this).isPresent(),
            isTrading(),
            isNoAi(),
            level.getGameRules().get(GameRules.MOB_GRIEFING)
        )) {
            return;
        }
        final Optional<BlockPos> deposit = BlockPos.betweenClosedStream(
                blockPosition().offset(-8, -3, -8),
                blockPosition().offset(8, 3, 8)
            )
            .filter(position -> level.getBlockState(position).is(CreatureBehaviorTags.Blocks.HOBGOBLIN_DEPOSIT_CONTAINERS))
            .filter(position -> !HopperBlockEntity.getContainerOrHandlerAt(level, position, null).isEmpty())
            .min(Comparator.comparingDouble(position -> distanceToSqr(Vec3.atCenterOf(position))));
        final Optional<ItemEntity> looseItem = level.getEntitiesOfClass(
                ItemEntity.class,
                getBoundingBox().inflate(8.0),
                item -> item.isAlive() && item.getItem().is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES)
            ).stream()
            .min(Comparator.comparingDouble(this::distanceToSqr));
        final boolean hasCargo = IntStream.range(0, getInventory().getContainerSize())
            .mapToObj(getInventory()::getItem)
            .anyMatch(stack -> stack.is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES));
        switch (HobgoblinWorkRules.nextAction(hasCargo, deposit.isPresent(), looseItem.isPresent())) {
            case DEPOSIT -> depositCargo(level, deposit.orElseThrow());
            case COLLECT -> collectLooseItem(level, looseItem.orElseThrow());
            case IDLE -> {
            }
        }
    }

    private void collectLooseItem(final ServerLevel level, final ItemEntity item) {
        if (distanceToSqr(item) > 4.0) {
            getNavigation().moveTo(item, 0.9);
            return;
        }
        InventoryCarrier.pickUpItem(level, this, this, item);
    }

    private void depositCargo(final ServerLevel level, final BlockPos position) {
        if (distanceToSqr(Vec3.atCenterOf(position)) > 9.0) {
            getNavigation().moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.9);
            return;
        }
        final ContainerOrHandler destination = HopperBlockEntity.getContainerOrHandlerAt(level, position, null);
        if (destination.isEmpty()) {
            return;
        }
        IntStream.range(0, getInventory().getContainerSize())
            .filter(slot -> getInventory().getItem(slot).is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES))
            .findFirst()
            .ifPresent(slot -> {
                final ItemStack source = getInventory().getItem(slot);
                final ItemStack remainder = depositInto(destination, source);
                getInventory().setItem(slot, remainder);
            });
    }

    private ItemStack depositInto(final ContainerOrHandler destination, final ItemStack source) {
        final Container container = destination.container();
        if (container != null) {
            return HopperBlockEntity.addItem(getInventory(), container, source.copy(), null);
        }
        final ItemStack remainder = source.copy();
        final int inserted = ResourceHandlerUtil.insertStacking(
            destination.itemHandler(),
            ItemResource.of(source),
            source.getCount(),
            null
        );
        remainder.shrink(inserted);
        return remainder;
    }

    private boolean isPatronBoss() {
        return KoboldBossRules.isBoss(kind);
    }

    private boolean isMineable(final ServerLevel level, final BlockPos position, final ItemStack tool) {
        final BlockState state = level.getBlockState(position);
        return state.is(WarlockeryTags.Blocks.HOBGOBLIN_MINEABLES)
            && state.getDestroySpeed(level, position) >= 0.0F
            && tool.isCorrectToolForDrops(state);
    }

    private void equipMiningTool(final ServerLevel level, final Player player, final ItemStack supplied) {
        final ItemStack previous = getMainHandItem().copy();
        final ItemStack equipped = supplied.copyWithCount(1);
        if (!player.hasInfiniteMaterials()) {
            supplied.shrink(1);
        }
        setItemSlotAndDropWhenKilled(EquipmentSlot.MAINHAND, equipped);
        setPersistenceRequired();
        prospectingCooldown = 0;
        swing(InteractionHand.MAIN_HAND);
        if (!previous.isEmpty()) {
            spawnAtLocation(level, previous);
        }
    }

    private void dropMinedResources(
        final ServerLevel level,
        final BlockPos position,
        final BlockState state,
        final List<ItemStack> drops,
        final ItemStack tool,
        final HobgoblinMiningRules.MiningProfile profile
    ) {
        state.spawnAfterBreak(level, position, tool, false);
        processedDrops(level, state, drops, profile).forEach(stack -> Block.popResource(level, position, stack));
        if (HobgoblinMiningRules.findsKoboldite(profile, random.nextFloat())) {
            Block.popResource(level, position, new ItemStack(ModItems.ALL.get("ingredient_delvealloydust").get()));
        }
    }

    private List<ItemStack> processedDrops(
        final ServerLevel level,
        final BlockState state,
        final List<ItemStack> drops,
        final HobgoblinMiningRules.MiningProfile profile
    ) {
        if (profile.autoSmeltChance() == 0.0F
            || !state.is(WarlockeryTags.Blocks.HOBGOBLIN_AUTO_SMELTABLE_ORES)) {
            return drops;
        }
        final int multiplier = HobgoblinMiningRules.autoSmeltMultiplier(
            profile,
            random.nextFloat(),
            random.nextFloat()
        );
        if (multiplier == 0) {
            return drops;
        }
        return drops.stream().flatMap(stack -> smeltedStacks(level, stack, multiplier).stream()).toList();
    }

    private static List<ItemStack> smeltedStacks(
        final ServerLevel level,
        final ItemStack inputStack,
        final int multiplier
    ) {
        final SingleRecipeInput input = new SingleRecipeInput(inputStack.copyWithCount(1));
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
            .map(recipe -> recipe.value().assemble(input))
            .filter(result -> !result.isEmpty())
            .map(result -> splitResult(
                result,
                Math.multiplyExact(Math.multiplyExact(result.getCount(), inputStack.getCount()), multiplier)
            ))
            .orElseGet(() -> List.of(inputStack));
    }

    private static List<ItemStack> splitResult(final ItemStack result, final int total) {
        return IntStream.iterate(
                total,
                remaining -> remaining > 0,
                remaining -> remaining - Math.min(remaining, result.getMaxStackSize())
            )
            .mapToObj(remaining -> result.copyWithCount(Math.min(remaining, result.getMaxStackSize())))
            .toList();
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("WarlockeryKoboldProfession", koboldProfession.id);
        output.putInt("WarlockeryProspectingCooldown", prospectingCooldown);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        koboldProfession = KoboldProfession.byId(input.getStringOr("WarlockeryKoboldProfession", "prospector"));
        prospectingCooldown = input.getIntOr("WarlockeryProspectingCooldown", 0);
    }

    public enum KoboldProfession {
        MINER("miner", Blocks.STONECUTTER), SMITH("smith", Blocks.BLAST_FURNACE),
        SHAMAN("shaman", Blocks.BREWING_STAND), PROSPECTOR("prospector", Blocks.CARTOGRAPHY_TABLE);

        private final String id;
        private final net.minecraft.world.level.block.Block workstation;

        KoboldProfession(final String id, final net.minecraft.world.level.block.Block workstation) {
            this.id = id;
            this.workstation = workstation;
        }

        static KoboldProfession byId(final String id) {
            return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst().orElse(PROSPECTOR);
        }
    }
}
