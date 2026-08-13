package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

public final class WerewolfHunterEntity extends Pillager implements ArcaneCreature {
    public WerewolfHunterEntity(final EntityType<? extends Pillager> type, final Level level) {
        super(type, level);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.WEREWOLF_HUNTER;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, WerewolfEntity.class, true));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, ArcaneMob.class, 10, true, false,
            (mob, level) -> mob instanceof ArcaneMob arcaneMob && arcaneMob.creatureKind().isVampiric()));
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        TacticalCombatRuntime.tick(this, level, CreatureKind.WEREWOLF_HUNTER);
        AmbientActivityRuntime.tick(this, level, CreatureKind.WEREWOLF_HUNTER);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            TacticalCombatRuntime.rememberIncomingThreat(this, level, source);
        }
        return hurt;
    }

    @Override
    public boolean canUseNonMeleeWeapon(final ItemStack item) {
        return item.getItem() instanceof CrossbowItem;
    }

    @Override
    protected void populateDefaultEquipmentSlots(final RandomSource random, final DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.ALL.get("ingredient_bolt_silver").get(), 64));
    }
}
