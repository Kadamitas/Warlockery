package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NamedHumanoidEntityRegressionTest {
    @Test
    void namiAndNaamahConstructorsExposeTheirNames() throws IOException {
        assertVisibleTranslatedName("NamiEntity", "entity.warlockery.nami");
        assertVisibleTranslatedName("NaamahEntity", "entity.warlockery.naamah");
    }

    private static void assertVisibleTranslatedName(final String className, final String translationKey)
        throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/entity/" + className + ".java"
        ));
        final String constructor = constructorBody(source, className);
        assertTrue(constructor.contains("setCustomName(Component.translatable(\"" + translationKey + "\"))"));
        assertTrue(constructor.contains("setCustomNameVisible(true)"));
    }

    private static String constructorBody(final String source, final String className) {
        final int signature = source.indexOf("public " + className + "(");
        assertTrue(signature >= 0, className + " constructor must exist");
        final int openingBrace = source.indexOf('{', signature);
        assertTrue(openingBrace >= 0, className + " constructor must have a body");
        int depth = 1;
        for (int index = openingBrace + 1; index < source.length(); index++) {
            switch (source.charAt(index)) {
                case '{' -> depth++;
                case '}' -> depth--;
                default -> {
                }
            }
            if (depth == 0) {
                return source.substring(openingBrace + 1, index);
            }
        }
        throw new AssertionError(className + " constructor body is incomplete");
    }
}
