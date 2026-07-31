package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.entity.InterpolationHandler;
import org.junit.jupiter.api.Test;

final class BroomInterpolationContractTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/entity/BroomEntity.java"
    );

    @Test
    void exposesVanillaInterpolationHandler() throws ReflectiveOperationException {
        final Method method = BroomEntity.class.getMethod("getInterpolation");
        final Field field = BroomEntity.class.getDeclaredField("interpolation");
        assertEquals(InterpolationHandler.class, method.getReturnType());
        assertEquals(InterpolationHandler.class, field.getType());
    }

    @Test
    void usesThreeStepSmoothingOnClientTicks() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("new InterpolationHandler(this, 3)"));
        assertTrue(source.contains("if (level().isClientSide())"));
        assertTrue(source.contains("interpolation.interpolate();"));
    }
}
