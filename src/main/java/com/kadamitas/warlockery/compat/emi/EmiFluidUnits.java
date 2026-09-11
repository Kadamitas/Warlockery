package com.kadamitas.warlockery.compat.emi;

final class EmiFluidUnits {
    private EmiFluidUnits() {}
    static long amount(int milliBuckets) {
        return com.kadamitas.warlockery.util.FluidContents.dropletsFromMilliBuckets(milliBuckets);
    }
}
