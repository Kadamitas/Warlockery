package com.kadamitas.warlockery.compat.fabric;

@FunctionalInterface
public interface EnergyReserve {
    long extract(long maximum, boolean simulate);
}
