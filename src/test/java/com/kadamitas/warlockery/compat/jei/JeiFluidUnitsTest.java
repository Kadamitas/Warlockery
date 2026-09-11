package com.kadamitas.warlockery.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

final class JeiFluidUnitsTest {
    @Test void oneBucketRecipeUsesOneNativeJeiBucket() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Class<?> slotType = Class.forName("mezz.jei.api.gui.builder.IRecipeSlotBuilder");
        AtomicLong recorded = new AtomicLong(-1);
        Object slot = Proxy.newProxyInstance(slotType.getClassLoader(), new Class<?>[] {slotType}, (proxy, method, arguments) -> {
            if (method.getName().equals("add") && arguments.length == 2 && arguments[1] instanceof Number amount) recorded.set(amount.longValue());
            return proxy;
        });
        var method = JeiIngredients.class.getDeclaredMethod("addFluid", slotType, String.class, int.class);
        method.setAccessible(true);
        method.invoke(null, slot, "minecraft:water", 1_000);
        assertEquals(81_000L, recorded.get(), "Fabric JEI expects droplets, while machine recipe JSON stores mB");
    }
}
