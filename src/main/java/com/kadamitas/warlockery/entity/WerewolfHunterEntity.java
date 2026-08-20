package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModItems;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class WerewolfHunterEntity extends Pillager implements ArcaneCreature {
    private static final String STATE_KEY = "WarlockeryWerewolfHunter";

    private final WerewolfHunterRuntime.Counters hunterCounters = new WerewolfHunterRuntime.Counters();
    private WerewolfHunterState hunterState;
    private net.minecraft.core.BlockPos lastQuarrySeen;

    public WerewolfHunterEntity(final EntityType<? extends Pillager> type, final Level level) {
        super(type, level);
        hunterState = WerewolfHunterState.empty(getUUID(), level.getGameTime());
        normalizeRaidState();
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.WEREWOLF_HUNTER;
    }

    public WerewolfHunterState hunterState() {
        return hunterState;
    }

    public void setHunterState(final WerewolfHunterState state) {
        hunterState = state == null ? WerewolfHunterState.empty(getUUID(), level().getGameTime()) : state;
    }

    public WerewolfHunterRuntime.Counters hunterCounters() {
        return hunterCounters;
    }

    public java.util.Optional<net.minecraft.core.BlockPos> lastQuarrySeen() {
        return java.util.Optional.ofNullable(lastQuarrySeen);
    }

    public void rememberQuarrySeen(final net.minecraft.core.BlockPos position) {
        lastQuarrySeen = position == null ? null : position.immutable();
    }

    @Override
    public void performRangedAttack(final LivingEntity target, final float power) {
        if (!(level() instanceof ServerLevel serverLevel)
            || !WerewolfHunterRuntime.mayFireNow(this, serverLevel, target)) {
            hunterCounters.shotCancellations++;
            setTarget(null);
            stopUsingItem();
            setChargingCrossbow(false);
            return;
        }
        super.performRangedAttack(target, power);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Creaking.class, 8.0F, 1.0, 1.2));
        goalSelector.addGoal(3, new RangedCrossbowAttackGoal<>(this, 1.0, 14.0F));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 15.0F, 1.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    public int operationalTargetGoalCount() {
        return targetSelector.getAvailableGoals().size();
    }

    public List<String> operationalGoalNames() {
        return goalSelector.getAvailableGoals().stream()
            .map(goal -> goal.getGoal().getClass().getSimpleName())
            .toList();
    }

    @Override
    public boolean canJoinRaid() {
        return false;
    }

    @Override
    public boolean canJoinPatrol() {
        return false;
    }

    @Override
    public boolean canBeLeader() {
        return false;
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target) && WerewolfHunterRuntime.eligibleTarget(this, target);
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        normalizeRaidState();
        TacticalCombatRuntime.tick(this, level, CreatureKind.WEREWOLF_HUNTER);
        AmbientActivityRuntime.tick(this, level, CreatureKind.WEREWOLF_HUNTER);
        WerewolfHunterRuntime.tick(this, level);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            TacticalCombatRuntime.rememberIncomingThreat(this, level, source);
            WerewolfHunterRuntime.recordDirectAttack(this, level, source);
        }
        return hurt;
    }

    @Override
    public boolean canUseNonMeleeWeapon(final ItemStack item) {
        return item.getItem() instanceof CrossbowItem;
    }

    @Override
    public ItemStack getProjectile(final ItemStack weapon) {
        if (!(weapon.getItem() instanceof ProjectileWeaponItem projectileWeapon)) {
            return ItemStack.EMPTY;
        }
        final ItemStack held = ProjectileWeaponItem.getHeldProjectile(
            this, projectileWeapon.getSupportedHeldProjectiles()
        );
        return held.is(silverBolt()) ? held : ItemStack.EMPTY;
    }

    public int silverBoltCount() {
        final ItemStack offhand = getOffhandItem();
        return offhand.is(silverBolt()) ? offhand.getCount() : 0;
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final ItemStack offered = player.getItemInHand(hand);
        if (!offered.is(silverBolt())) {
            return super.mobInteract(player, hand);
        }
        if (!(level() instanceof ServerLevel)) {
            return InteractionResult.CONSUME;
        }
        if (WerewolfHunterRules.resupplyRefused(
            isChargingCrossbow(),
            hunterState.intent() == WerewolfHunterRules.Intent.ENGAGE,
            hunterState.intent() == WerewolfHunterRules.Intent.RETREAT,
            hunterState.evidence().stream().anyMatch(entry ->
                entry.type() == WerewolfHunterRules.EvidenceType.DIRECT_ATTACK
                    && entry.valid(level().getGameTime())
                    && entry.sourceId().map(player.getUUID()::equals).orElse(false))
        )) {
            return InteractionResult.FAIL;
        }
        final int accepted = WerewolfHunterRules.acceptedResupply(offered.getCount(), silverBoltCount());
        if (accepted <= 0) {
            return InteractionResult.FAIL;
        }
        final ItemStack offhand = getOffhandItem();
        if (offhand.is(silverBolt())) {
            offhand.grow(accepted);
        } else if (offhand.isEmpty()) {
            setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(silverBolt(), accepted));
        } else {
            return InteractionResult.FAIL;
        }
        if (!player.hasInfiniteMaterials()) {
            offered.shrink(accepted);
        }
        return InteractionResult.SUCCESS;
    }

    private net.minecraft.world.item.Item silverBolt() {
        return ModItems.ALL.get("ingredient_bolt_silver").get();
    }

    @Override
    protected void populateDefaultEquipmentSlots(final RandomSource random, final DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(
            ModItems.ALL.get("ingredient_bolt_silver").get(), WerewolfHunterRules.DEFAULT_SILVER_BOLTS
        ));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason reason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
        normalizeRaidState();
        return result;
    }

    private void normalizeRaidState() {
        final var followRange = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        setCanJoinRaid(false);
        setPatrolLeader(false);
        setCanPickUpLoot(false);
        setCelebrating(false);
        final Raid legacyRaid = getCurrentRaid();
        if (legacyRaid != null && level() instanceof ServerLevel serverLevel) {
            legacyRaid.removeFromRaid(serverLevel, this, false);
            setCurrentRaid(null);
        }
        final ItemStack offhand = getOffhandItem();
        if (offhand.is(silverBolt()) && offhand.getCount() > WerewolfHunterRules.MAX_SILVER_BOLTS) {
            offhand.setCount(WerewolfHunterRules.MAX_SILVER_BOLTS);
        }
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, hunterState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        hunterState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> WerewolfHunterState.read(tag, getUUID(), level().getGameTime()))
            .orElse(WerewolfHunterState.empty(getUUID(), level().getGameTime()));
        setChargingCrossbow(false);
        lastQuarrySeen = null;
        normalizeRaidState();
    }
}
