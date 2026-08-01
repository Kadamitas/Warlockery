package com.kadamitas.warlockery.fabric.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class LivingDamageContext {
    private final LivingEntity entity;
    private final DamageSource source;
    private float amount;
    private boolean canceled;

    public LivingDamageContext(final LivingEntity entity, final DamageSource source, final float amount) {
        this.entity = entity;
        this.source = source;
        this.amount = amount;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public DamageSource getSource() {
        return source;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(final float amount) {
        this.amount = Math.max(0.0F, amount);
    }

    public void setNewDamage(final float amount) {
        setAmount(amount);
    }

    public boolean isCanceled() {
        return canceled || amount <= 0.0F;
    }

    public void cancel() {
        canceled = true;
        amount = 0.0F;
    }
}
