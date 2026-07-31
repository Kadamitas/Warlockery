package com.kadamitas.warlockery.registry;

@FunctionalInterface
public interface ContentFactory<P, T> {
    T create(P properties);
}
