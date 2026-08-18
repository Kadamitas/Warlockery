package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.ModSounds;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public class HobgoblinEntity extends Villager implements ArcaneCreature {
    private final CreatureKind kind;
    private final CreatureBehavior behavior;
    private GoblinProfession goblinProfession = GoblinProfession.PROSPECTOR;
    private int prospectingCooldown;
    private @Nullable BlockPos raidCenter;
    private int raidWave;
    private boolean raidLeader;
    private long nextFlowerGiftTime;

    public HobgoblinEntity(final EntityType<? extends Villager> type, final Level level, final CreatureKind kind) {
        super(type, level);
        this.kind = kind;
        this.behavior = CreatureBehaviorFactory.create(kind);
        if (GoblinLifecycleRules.fleesHumanVillagers(kind)) {
            this.goalSelector.addGoal(1, new AvoidEntityGoal<>(
                this,
                Villager.class,
                target -> !isTrading() && GoblinHostilityRules.isHumanVillager(target.getType()),
                12.0F,
                0.85,
                1.25,
                target -> true
            ));
        }
        if (GoblinHostilityRules.raidsVillagers(kind)) {
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this,
                Villager.class,
                1,
                false,
                false,
                (target, serverLevel) -> GoblinHostilityRules.canTarget(kind, target.getType())
                    && behavior.canAttack(this, target)
            ));
        }
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

    public GoblinProfession goblinProfession() {
        return goblinProfession;
    }

    public void assignProfessionFromVillage() {
        this.goblinProfession = Arrays.stream(GoblinProfession.values())
            .filter(role -> this.level().getBlockStates(this.getBoundingBox().inflate(10)).anyMatch(state -> state.is(role.workstation())))
            .min(Comparator.comparingInt(Enum::ordinal))
            .orElseGet(() -> GoblinProfession.values()[random.nextInt(GoblinProfession.values().length)]);
        refreshDisplayName();
        this.getOffers().clear();
    }

    public void assignRandomProfession() {
        this.goblinProfession = GoblinProfession.values()[random.nextInt(GoblinProfession.values().length)];
        refreshDisplayName();
        this.getOffers().clear();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason spawnReason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        assignRandomProfession();
        syncVanillaProfession(level.registryAccess());
        return result;
    }

    public static boolean checkNaturalSpawnRules(
        final EntityType<HobgoblinEntity> type,
        final ServerLevelAccessor level,
        final EntitySpawnReason spawnReason,
        final BlockPos position,
        final RandomSource random
    ) {
        return GoblinLifecycleRules.canSpawnNaturally(
            CreatureKind.HOBGOBLIN,
            level.getLevel().isVillage(position)
        ) && Mob.checkMobSpawnRules(type, level, spawnReason, position, random);
    }

    @Override
    protected void updateTrades(final ServerLevel level) {
        final long seed = getUUID().getMostSignificantBits()
            ^ getUUID().getLeastSignificantBits()
            ^ ((long) goblinProfession.ordinal() << 32)
            ^ kind.ordinal();
        this.getOffers().addAll(GoblinTradeCatalog.createOffers(
            kind,
            goblinProfession,
            seed,
            getVillagerData().level()
        ));
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        syncVanillaProfession(level.registryAccess());
        super.customServerAiStep(level);
        if (isTrading()) {
            getNavigation().stop();
            setTarget(null);
        }
        behavior.tick(this, level);
        TacticalCombatRuntime.tick(this, level, kind);
        GoblinSettlementLifeRuntime.tick(this, level);
        AmbientActivityRuntime.tick(this, level, kind);
        if (fleeHumanVillager(level)) {
            return;
        }
        if (tickCount % 20 == 0) {
            performServitudeWork(level);
        }
        if (goblinProfession != GoblinProfession.MINER || isTrading() || isNoAi()) {
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
            if (level() instanceof ServerLevel serverLevel) {
                syncVanillaProfession(serverLevel.registryAccess());
            }
            final InteractionResult result = super.mobInteract(player, hand);
            if (isTrading()) {
                getNavigation().stop();
                setTarget(null);
            }
            return result;
        }
        if (level() instanceof ServerLevel serverLevel) {
            equipMiningTool(serverLevel, player, supplied);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean canBreed() {
        return super.canBreed()
            && level() instanceof ServerLevel level
            && GoblinSettlementLifeRuntime.hasAvailableHomeForChild(this, level);
    }

    @Override
    public @Nullable Villager getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
        if (!(partner instanceof HobgoblinEntity other)
            || !GoblinLifecycleRules.canReproduce(kind, other.kind)) {
            return null;
        }
        final Entity offspring = getType().create(level, EntitySpawnReason.BREEDING);
        if (!(offspring instanceof HobgoblinEntity child)) {
            return null;
        }
        child.goblinProfession = random.nextBoolean() ? goblinProfession : other.goblinProfession;
        child.syncVanillaProfession(level.registryAccess());
        child.setVillagerDataFinalized(true);
        child.refreshDisplayName();
        return child;
    }

    @Override
    public EntityDimensions getDefaultDimensions(final Pose pose) {
        final EntityDimensions dimensions = getType().getDimensions();
        return isBaby() ? dimensions.scale(GoblinLifecycleRules.BABY_DIMENSION_SCALE) : dimensions;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return (isTrading() ? soundSet().trade() : soundSet().ambient()).get();
    }

    @Override
    protected SoundEvent getHurtSound(final DamageSource source) {
        return soundSet().hurt().get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return soundSet().death().get();
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return soundSet().trade().get();
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(final boolean validTrade) {
        return (validTrade ? soundSet().trade() : soundSet().reject()).get();
    }

    @Override
    public void playWorkSound() {
        makeSound(soundSet().work().get());
    }

    @Override
    public boolean wantsToPickUp(final ServerLevel level, final ItemStack stack) {
        return GoblinSettlementLifeRules.participates(kind, isVillageRaider(), isPatronBoss())
            && (stack.is(ItemTags.DIRT)
                || stack.is(ItemTags.LOGS)
                || stack.is(net.minecraftforge.common.Tags.Items.NATURAL_LOGS))
            || CreatureBehaviorState.owner(this).isPresent()
            && stack.is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES)
            || super.wantsToPickUp(level, stack);
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        if (isBaby()) {
            return false;
        }
        if (GoblinLifecycleRules.fleesHumanVillagers(kind)
            && GoblinHostilityRules.isHumanVillager(target.getType())) {
            return false;
        }
        if (GoblinHostilityRules.canTarget(kind, target.getType())) {
            return behavior.canAttack(this, target);
        }
        return behavior.canAttack(this, target) && super.canAttack(target);
    }

    public void joinVillageRaid(final BlockPos center, final int wave, final boolean leader) {
        raidCenter = center.immutable();
        raidWave = wave;
        raidLeader = leader;
        setPersistenceRequired();
    }

    public void leaveVillageRaid() {
        raidCenter = null;
        raidWave = 0;
        raidLeader = false;
    }

    public Optional<BlockPos> raidCenter() {
        return Optional.ofNullable(raidCenter);
    }

    public int raidWave() {
        return raidWave;
    }

    public boolean isRaidLeader() {
        return raidLeader;
    }

    public boolean isVillageRaider() {
        return kind == CreatureKind.GOBLIN && raidCenter != null && raidWave > 0;
    }

    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        final boolean hurt = PrimaryAttackModifier.withDamageBonus(
            this,
            behavior.attackDamageBonus(this, level),
            () -> super.doHurtTarget(level, target)
        );
        if (hurt) {
            behavior.afterAttack(this, level, target);
        }
        return hurt;
    }

    @Override
    public boolean causeFallDamage(
        final double fallDistance,
        final float damageModifier,
        final DamageSource damageSource
    ) {
        return false;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            TacticalCombatRuntime.rememberIncomingThreat(this, level, source);
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
            .filter(position -> HopperBlockEntity.getContainerAt(level, position) != null)
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
        final Container container = HopperBlockEntity.getContainerAt(level, position);
        if (container == null) {
            return;
        }
        IntStream.range(0, getInventory().getContainerSize())
            .filter(slot -> getInventory().getItem(slot).is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES))
            .findFirst()
            .ifPresent(slot -> {
                final ItemStack source = getInventory().getItem(slot);
                final ItemStack remainder = HopperBlockEntity.addItem(
                    getInventory(),
                    container,
                    source.copy(),
                    null
                );
                getInventory().setItem(slot, remainder);
            });
    }

    private boolean isPatronBoss() {
        return GoblinBossRules.isBoss(kind);
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
        if (HobgoblinMiningRules.findsGoblinite(profile, random.nextFloat())) {
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
        output.putString("WarlockeryGoblinProfession", goblinProfession.id());
        output.putInt("WarlockeryProspectingCooldown", prospectingCooldown);
        output.putLong("WarlockeryNextFlowerGift", nextFlowerGiftTime);
        if (raidCenter != null) {
            output.putLong("WarlockeryGoblinRaidCenter", raidCenter.asLong());
            output.putInt("WarlockeryGoblinRaidWave", raidWave);
            output.putBoolean("WarlockeryGoblinRaidLeader", raidLeader);
        }
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        goblinProfession = GoblinProfession.byId(input.getStringOr("WarlockeryGoblinProfession", "prospector"));
        prospectingCooldown = input.getIntOr("WarlockeryProspectingCooldown", 0);
        nextFlowerGiftTime = Math.max(0L, input.getLongOr("WarlockeryNextFlowerGift", 0L));
        final long encodedCenter = input.getLongOr("WarlockeryGoblinRaidCenter", Long.MIN_VALUE);
        raidCenter = encodedCenter == Long.MIN_VALUE ? null : BlockPos.of(encodedCenter);
        raidWave = input.getIntOr("WarlockeryGoblinRaidWave", 0);
        raidLeader = input.getBooleanOr("WarlockeryGoblinRaidLeader", false);
        if (!hasCustomName()) {
            refreshDisplayName();
        }
    }

    private void syncVanillaProfession(final HolderGetter.Provider registries) {
        if (getVillagerXp() == 0) {
            setVillagerXp(1);
        }
        if (getVillagerData().profession().is(goblinProfession.engineProfession())) {
            return;
        }
        setVillagerData(getVillagerData().withProfession(registries, goblinProfession.engineProfession()));
        setVillagerDataFinalized(true);
    }

    private void refreshDisplayName() {
        final String species = kind == CreatureKind.GOBLIN ? "goblin" : "hobgoblin";
        setCustomName(Component.translatable("entity.warlockery." + species + ".profession." + goblinProfession.id()));
        setCustomNameVisible(true);
    }

    private ModSounds.CreatureSoundSet soundSet() {
        return kind == CreatureKind.GOBLIN ? ModSounds.GOBLIN : ModSounds.HOBGOBLIN;
    }

    long nextFlowerGiftTime() {
        return nextFlowerGiftTime;
    }

    void recordFlowerGift(final long nextGiftTime) {
        nextFlowerGiftTime = Math.max(nextFlowerGiftTime, nextGiftTime);
    }

    private boolean fleeHumanVillager(final ServerLevel level) {
        if (!GoblinLifecycleRules.fleesHumanVillagers(kind) || isTrading()) {
            return false;
        }
        final Optional<Villager> nearestHuman = level.getEntitiesOfClass(
                Villager.class,
                getBoundingBox().inflate(12.0, 4.0, 12.0),
                villager -> GoblinHostilityRules.isHumanVillager(villager.getType())
            ).stream()
            .min(Comparator.comparingDouble(this::distanceToSqr));
        if (nearestHuman.isEmpty()) {
            return false;
        }
        final Vec3 separation = position().subtract(nearestHuman.orElseThrow().position()).multiply(1.0, 0.0, 1.0);
        if (separation.lengthSqr() > 1.0E-4) {
            final Vec3 escapeVelocity = separation.normalize().scale(0.18);
            setDeltaMovement(escapeVelocity.x, getDeltaMovement().y, escapeVelocity.z);
        }
        if (tickCount % 10 == 0 || getNavigation().isDone()) {
            final Vec3 escape = DefaultRandomPos.getPosAway(this, 16, 7, nearestHuman.orElseThrow().position());
            if (escape != null) {
                getNavigation().moveTo(escape.x, escape.y, escape.z, 1.25);
            }
        }
        return true;
    }

    /*
     * `acquireLocalRaidTarget` and the `GoblinRaidRuntime.coordinate` call that followed it were
     * removed rather than repaired. Both were guarded by `kind == CreatureKind.GOBLIN`, and after
     * the F10 split `ModEntities.GOBLIN` constructs the dedicated `GoblinEntity`, so no instance of
     * this class can ever report that kind again: the guard is unsatisfiable and both paths were
     * unreachable. F10's `GoblinEnclaveRuntime` owns exact-Goblin targeting and assault movement.
     */
}
