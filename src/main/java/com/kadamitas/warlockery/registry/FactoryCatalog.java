package com.kadamitas.warlockery.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record FactoryCatalog<P, T>(
    String contentType,
    Map<String, ContentFactory<P, T>> factories
) {
    public FactoryCatalog {
        contentType = Objects.requireNonNull(contentType);
        factories = Collections.unmodifiableMap(new LinkedHashMap<>(factories));
    }

    public boolean supports(final String id) {
        return factories.containsKey(id);
    }

    public Set<String> ids() {
        return factories.keySet();
    }

    public Optional<ContentFactory<P, T>> factoryFor(final String id) {
        return Optional.ofNullable(factories.get(id));
    }

    public T create(final String id, final P properties) {
        return factoryFor(id)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported " + contentType + ": " + id))
            .create(properties);
    }

    public static <P, T> Map.Entry<String, ContentFactory<P, T>> entry(
        final String id,
        final ContentFactory<P, T> factory
    ) {
        return Map.entry(id, factory);
    }
}
