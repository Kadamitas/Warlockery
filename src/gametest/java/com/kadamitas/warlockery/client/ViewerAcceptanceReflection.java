package com.kadamitas.warlockery.client;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

final class ViewerAcceptanceReflection {
    private ViewerAcceptanceReflection() {}
    static Class<?> type(String name) {
        try { return Class.forName(name); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Missing viewer class " + name, failure); }
    }
    static Object field(Object object, String name) {
        Class<?> type = object instanceof Class<?> c ? c : object.getClass();
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                var field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object instanceof Class<?> ? null : object);
            } catch (NoSuchFieldException ignored) { }
            catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot read " + type + "." + name, failure); }
        }
        throw new AssertionError("Missing viewer field " + type + "." + name);
    }
    static Object call(Object object, String name, Object... args) {
        Class<?> type = object instanceof Class<?> c ? c : object.getClass();
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length || !accepts(method.getParameterTypes(), args)) continue;
                if (object instanceof Class<?> && !Modifier.isStatic(method.getModifiers())) continue;
                try { method.setAccessible(true); return method.invoke(object instanceof Class<?> ? null : object, args); }
                catch (ReflectiveOperationException failure) { throw new AssertionError("Viewer call failed " + type + "." + name, failure); }
            }
        }
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length || !accepts(method.getParameterTypes(), args)) continue;
            try { method.setAccessible(true); return method.invoke(object instanceof Class<?> ? null : object, args); }
            catch (ReflectiveOperationException failure) { throw new AssertionError("Viewer interface call failed " + type + "." + name, failure); }
        }
        throw new AssertionError("Missing viewer method " + type + "." + name + " " + Arrays.toString(args));
    }
    private static boolean accepts(Class<?>[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            if (args[i] == null) { if (parameters[i].isPrimitive()) return false; else continue; }
            Class<?> type = parameters[i];
            if (type.isPrimitive()) type = type == boolean.class ? Boolean.class : type == int.class ? Integer.class
                : type == long.class ? Long.class : type == double.class ? Double.class : type == float.class ? Float.class : type;
            if (!type.isInstance(args[i])) return false;
        }
        return true;
    }
    static List<?> list(Object value) {
        if (value instanceof List<?> list) return list;
        if (value instanceof java.util.Collection<?> collection) return List.copyOf(collection);
        if (value instanceof Map<?, ?> map) return List.copyOf(map.values());
        if (value instanceof java.util.stream.Stream<?> stream) return stream.toList();
        throw new AssertionError("Expected collection, got " + value);
    }
    static int integer(Object object, String method) { return ((Number) call(object, method)).intValue(); }
    static ViewerAcceptanceDriver.Stack item(ItemStack stack) {
        return new ViewerAcceptanceDriver.Stack("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
