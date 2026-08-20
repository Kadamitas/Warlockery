package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.item.ResourceCompatibilityTags;
import com.kadamitas.warlockery.item.SympatheticBinding;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class BrambleColossusEntity extends Monster implements ArcaneCreature {
    public static final double BASE_MAX_HEALTH = 36.0D;
    public static final double BASE_ATTACK_DAMAGE = 7.0D;
    public static final double BASE_MOVEMENT_SPEED = 0.3D;
    public static final int XP_REWARD = 5;
    static final String STATE_KEY = "WarlockeryColossusState";

    private BrambleColossusState colossusState = BrambleColossusState.empty();
    private final BrambleColossusRuntime.TransientState transientState = new BrambleColossusRuntime.TransientState();
    private final BrambleColossusRuntime.Counters counters = new BrambleColossusRuntime.Counters();

    public BrambleColossusEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level); xpReward = XP_REWARD;
    }
    @Override public CreatureKind creatureKind() { return CreatureKind.BRAMBLE_COLOSSUS; }
    public BrambleColossusState colossusState() { return colossusState; }
    public void setColossusState(BrambleColossusState state) { colossusState = state == null ? BrambleColossusState.empty() : state; }
    public void recordPost(BlockPos position) { colossusState = colossusState.postedAt(position); }
    public BrambleColossusRuntime.TransientState colossusTransient() { return transientState; }
    public BrambleColossusRuntime.Counters colossusCounters() { return counters; }

    @Override protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(9, new LookOnlyGoal(this));
    }
    private static final class LookOnlyGoal extends RandomLookAroundGoal {
        LookOnlyGoal(BrambleColossusEntity mob) { super(mob); setFlags(EnumSet.of(Flag.LOOK)); }
    }
    @Override protected void customServerAiStep(ServerLevel level) { super.customServerAiStep(level); BrambleColossusRuntime.tick(this, level); }
    @Override public boolean canAttack(LivingEntity target) { return super.canAttack(target) && BrambleColossusRuntime.legalSubject(this, target); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float before = getHealth() + getAbsorptionAmount();
        boolean result = super.hurtServer(level, source, amount);
        float accepted = before - (getHealth() + getAbsorptionAmount());
        if (result && accepted > 0 && source.getEntity() instanceof LivingEntity attacker) BrambleColossusRuntime.onAcceptedDamage(this, level, attacker, accepted);
        return result;
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);
        if (!CreatureBehaviorState.isOwnedBy(this, player.getUUID())) { player.sendOverlayMessage(Component.translatable("message.warlockery.creature.owner_required", getDisplayName())); return InteractionResult.FAIL; }
        ItemStack held = player.getItemInHand(hand);
        var binding = SympatheticBinding.read(held);
        if (binding.isPresent()) {
            var result = TreefydState.toggleAllowedResult(this, binding.orElseThrow());
            if (result == TreefydState.ToggleResult.FULL) return InteractionResult.FAIL;
            player.sendOverlayMessage(Component.translatable(result == TreefydState.ToggleResult.ADDED ? "message.warlockery.creature.treefyd.allowed" : "message.warlockery.creature.treefyd.removed", binding.orElseThrow().targetName()));
            return InteractionResult.SUCCESS;
        }
        if (held.is(ResourceCompatibilityTags.Items.SAFE_MAGICAL_PLANT_TOOLS)) {
            boolean wandering = TreefydState.toggleWandering(this);
            if (!wandering) BrambleColossusRuntime.cancelMovement(this);
            player.sendOverlayMessage(Component.translatable(wandering ? "message.warlockery.creature.treefyd.wandering" : "message.warlockery.creature.treefyd.guardian"));
            return InteractionResult.SUCCESS;
        }
        if (held.is(CreatureBehaviorTags.Items.HEART_OFFERINGS)) {
            var result = CreatureBehaviorState.empower(this, 1);
            if (!result.changed()) { player.sendOverlayMessage(Component.translatable("message.warlockery.creature.empowerment_full", getDisplayName())); return InteractionResult.FAIL; }
            held.consume(1, player); player.sendOverlayMessage(Component.translatable("message.warlockery.creature.empowered", getDisplayName(), result.after())); return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    @Override public boolean isPreventingPlayerRest(ServerLevel level, Player player) { return false; }
    @Override public Entity teleport(TeleportTransition transition) {
        if (transition.newLevel() == level()) return super.teleport(transition);
        var previous = colossusState;
        colossusState = colossusState.withoutPost();
        transientState.resetAfterDimensionChange();
        Entity moved = super.teleport(transition);
        if (moved == null) { colossusState = previous; return null; }
        if (moved instanceof BrambleColossusEntity colossus) {
            colossus.recordPost(colossus.blockPosition());
            colossus.transientState.resetAfterDimensionChange();
            BrambleColossusRuntime.cancelMovement(colossus);
        }
        return moved;
    }
    @Override public void remove(RemovalReason reason) { BrambleColossusRuntime.cancel(this); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); output.store(STATE_KEY, CompoundTag.CODEC, colossusState.write()); }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        colossusState = input.read(STATE_KEY, CompoundTag.CODEC).map(BrambleColossusState::read).orElseGet(BrambleColossusState::empty);
        if (colossusState.posted()) {
            var border=level().getWorldBorder();
            var post=colossusState.post().orElseThrow();
            post=new BlockPos(BrambleColossusRules.clampCoordinate(post.getX(),border.getMinX(),border.getMaxX()),
                BrambleColossusRules.clampBuildY(post.getY(),level().getMinY(),level().getMaxY()),
                BrambleColossusRules.clampCoordinate(post.getZ(),border.getMinZ(),border.getMaxZ()));
            colossusState=colossusState.postedAt(post);
        }
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) { setItemSlot(slot, ItemStack.EMPTY); setDropChance(slot,0.0F); }
        setHealth(Math.clamp(getHealth(),1.0F,getMaxHealth()));
        transientState.resetAfterLoad(); BrambleColossusRuntime.cancelMovement(this);
    }
}
