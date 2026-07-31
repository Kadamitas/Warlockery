package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.Warlockery;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class PrimaryAttackModifier {
    private static final Identifier BONUS_ID = Identifier.fromNamespaceAndPath(
        Warlockery.MOD_ID,
        "primary_attack_bonus"
    );

    private PrimaryAttackModifier() {
    }

    public static boolean withDamageBonus(
        final Mob attacker,
        final float bonus,
        final BooleanSupplier attack
    ) {
        final var damage = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null || bonus <= 0.0F) {
            return attack.getAsBoolean();
        }
        damage.addTransientModifier(new AttributeModifier(
            BONUS_ID,
            bonus,
            AttributeModifier.Operation.ADD_VALUE
        ));
        try {
            return attack.getAsBoolean();
        } finally {
            damage.removeModifier(BONUS_ID);
        }
    }
}
