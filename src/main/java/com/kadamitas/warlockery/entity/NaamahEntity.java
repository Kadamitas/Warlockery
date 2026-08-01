package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

public final class NaamahEntity extends ArcaneMob {
    public NaamahEntity(final EntityType<? extends Zombie> type, final Level level) {
        super(type, level, CreatureKind.NAAMAH);
        setCustomName(Component.translatable("entity.warlockery.naamah"));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        if (amount >= getHealth()
            && source.getEntity() instanceof ServerPlayer player
            && player.getStringUUID().equals(WarlockeryEntityData.get(this).getStringOr(
                SupernaturalProgressionRuntime.NAAMAH_TRIAL_OWNER,
                ""
            ))
            && SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE
            && SupernaturalProgression.level(player, SupernaturalProgression.Path.VAMPIRE) == 6) {
            setHealth(1.0F);
            clearFire();
            setTarget(null);
            getNavigation().stop();
            SupernaturalProgressionRuntime.recordNaamahDefeat(player);
            return true;
        }
        return super.hurtServer(level, source, amount);
    }
}
