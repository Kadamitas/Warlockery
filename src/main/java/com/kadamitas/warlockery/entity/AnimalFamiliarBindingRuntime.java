package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModEntities;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.player.Player;

public final class AnimalFamiliarBindingRuntime {
    private AnimalFamiliarBindingRuntime() {
    }

    public static AnimalFamiliarBindingRules.Kind kind(final Mob candidate) {
        if (candidate instanceof Cat) return AnimalFamiliarBindingRules.Kind.VANILLA_CAT;
        if (candidate instanceof Frog) return AnimalFamiliarBindingRules.Kind.VANILLA_FROG;
        return AnimalFamiliarBindingRules.Kind.EXISTING;
    }

    public static UUID tameOwner(final Mob candidate) {
        return candidate instanceof Cat cat && cat.isTame() && cat.getOwnerReference() != null
            ? cat.getOwnerReference().getUUID()
            : null;
    }

    public static boolean bind(final ServerLevel level, final Mob source, final Player caster, final int duration) {
        return bind(level, source, caster, duration, level::addFreshEntity);
    }

    static boolean bind(
        final ServerLevel level, final Mob source, final Player caster, final int duration,
        final Predicate<Mob> addReplacement
    ) {
        final var candidate = new AnimalFamiliarBindingRules.Candidate(
            kind(source), 0.0, source.getUUID(), tameOwner(source), source.isLeashed(),
            source.isPassenger(), source.isVehicle()
        );
        return switch (AnimalFamiliarBindingRules.outcome(candidate, caster.getUUID())) {
            case REJECT -> false;
            case BIND_IN_PLACE -> finishBinding(source, caster.getUUID(), duration);
            case REPLACE_WITH_FAMILIAR_CAT -> replace(level, source, caster.getUUID(), duration, "familiar_cat", addReplacement);
            case REPLACE_WITH_TOAD -> replace(level, source, caster.getUUID(), duration, "toad", addReplacement);
        };
    }

    private static boolean replace(
        final ServerLevel level, final Mob source, final UUID owner, final int duration, final String id,
        final Predicate<Mob> addReplacement
    ) {
        final var created = ModEntities.ALL.get(id).get().create(level, EntitySpawnReason.CONVERSION);
        if (!(created instanceof Mob replacement)) return false;
        replacement.setPos(source.position());
        replacement.setYRot(source.getYRot());
        replacement.setXRot(source.getXRot());
        replacement.setDeltaMovement(source.getDeltaMovement());
        replacement.setCustomName(source.getCustomName());
        replacement.setCustomNameVisible(source.isCustomNameVisible());
        replacement.setBaby(source.isBaby());
        if (source.isPersistenceRequired()) replacement.setPersistenceRequired();
        if (!finishBinding(replacement, owner, duration) || !addReplacement.test(replacement)) return false;
        source.discard();
        return true;
    }

    private static boolean finishBinding(final Mob entity, final UUID owner, final int duration) {
        if (!CreatureBehaviorState.bind(entity, owner)) return false;
        entity.setPersistenceRequired();
        entity.setTarget(null);
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 1));
        entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, 0));
        return true;
    }
}
