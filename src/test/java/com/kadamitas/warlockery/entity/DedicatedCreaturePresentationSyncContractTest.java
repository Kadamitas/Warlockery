package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

final class DedicatedCreaturePresentationSyncContractTest {
    @Test
    void corruptEnumOrdinalsFallBackInsteadOfEscapingOntoTheRenderThread() throws Exception {
        final Class<?> codec = Class.forName(
            "com.kadamitas.warlockery.entity.EntityPresentationSync"
        );
        final Method decode = codec.getDeclaredMethod("decode", int.class, Enum.class);
        decode.setAccessible(true);

        assertEquals(
            InfernalHierarchyRules.Intent.COMMAND,
            decode.invoke(null, InfernalHierarchyRules.Intent.COMMAND.ordinal(),
                InfernalHierarchyRules.Intent.IDLE)
        );
        assertEquals(
            InfernalHierarchyRules.Intent.IDLE,
            decode.invoke(null, -1, InfernalHierarchyRules.Intent.IDLE)
        );
        assertEquals(
            InfernalHierarchyRules.Intent.IDLE,
            decode.invoke(null, 255, InfernalHierarchyRules.Intent.IDLE)
        );
    }
}
