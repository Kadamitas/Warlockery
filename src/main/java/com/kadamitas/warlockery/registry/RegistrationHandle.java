package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public final class RegistrationHandle<T> implements Supplier<T> {
    private final Identifier id;
    private final Supplier<? extends T> factory;
    private Holder.Reference<T> holder;
    private T value;

    private RegistrationHandle(final Identifier id, final Supplier<? extends T> factory) {
        this.id = Objects.requireNonNull(id);
        this.factory = Objects.requireNonNull(factory);
    }

    public static <T> RegistrationHandle<T> create(final String path, final Supplier<? extends T> factory) {
        return new RegistrationHandle<>(Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path), factory);
    }

    public static <T> RegistrationHandle<T> external(final String path) {
        return create(path, () -> {
            throw new IllegalStateException("External registry handle requires bind(): " + path);
        });
    }

    public Identifier id() {
        return id;
    }

    public T register(final Registry<? super T> targetRegistry) {
        if (value != null) {
            return value;
        }
        holder = Registry.registerForHolder(Objects.requireNonNull(targetRegistry), id, factory.get());
        value = holder.value();
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public T bind(final Registry<? super T> targetRegistry, final T registeredValue) {
        if (value != null && value != registeredValue) {
            throw new IllegalStateException("Registration handle already bound: " + id);
        }
        Objects.requireNonNull(targetRegistry).get(id)
            .ifPresent(reference -> holder = (Holder.Reference) reference);
        value = Objects.requireNonNull(registeredValue);
        return value;
    }

    @Override
    public T get() {
        if (value == null) {
            throw new IllegalStateException("Registry value requested before initialization: " + id);
        }
        return value;
    }

    public Optional<Holder.Reference<T>> getHolder() {
        return Optional.ofNullable(holder);
    }
}
