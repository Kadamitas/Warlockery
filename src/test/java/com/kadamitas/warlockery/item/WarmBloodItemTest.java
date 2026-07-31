package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class WarmBloodItemTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void restoresTwoDropletsOnlyForVampires() {
        assertEquals(20, WarmBloodItem.reserveRestored(SupernaturalForm.VAMPIRE));
        assertEquals(0, WarmBloodItem.reserveRestored(SupernaturalForm.WEREWOLF));
        assertEquals(0, WarmBloodItem.reserveRestored(SupernaturalForm.NONE));
    }
}
