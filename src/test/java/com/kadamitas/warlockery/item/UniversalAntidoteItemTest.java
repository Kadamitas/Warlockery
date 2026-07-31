package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class UniversalAntidoteItemTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void curesPoisonAndWitherWithoutStrippingBeneficialEffects() {
        assertTrue(UniversalAntidoteItem.isCurable(MobEffects.POISON));
        assertTrue(UniversalAntidoteItem.isCurable(MobEffects.WITHER));
        assertFalse(UniversalAntidoteItem.isCurable(MobEffects.REGENERATION));
    }
}
