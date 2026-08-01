package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

public final class CustomBrewCloudRules {
    public static final String DELIVERY_KEY = "WarlockeryCustomBrewDelivery";
    private static final String NEXT_APPLICATION_PREFIX = "WarlockeryCustomBrewNext_";
    private static final int REAPPLICATION_DELAY = 20;

    private CustomBrewCloudRules() {
    }

    public static void mark(final Entity cloud, final CustomBrewDelivery delivery) {
        WarlockeryEntityData.get(cloud).putString(DELIVERY_KEY, delivery.id());
    }

    public static boolean isDelivery(final Entity cloud, final CustomBrewDelivery delivery) {
        return delivery.id().equals(WarlockeryEntityData.get(cloud).getStringOr(DELIVERY_KEY, ""));
    }

    public static Optional<CustomBrewDelivery> delivery(final Entity cloud) {
        return CustomBrewDelivery.find(WarlockeryEntityData.get(cloud).getStringOr(DELIVERY_KEY, ""));
    }

    public static boolean claim(final Entity cloud, final UUID target, final long gameTime) {
        final String key = NEXT_APPLICATION_PREFIX + target;
        final long nextApplication = WarlockeryEntityData.get(cloud).getLongOr(key, 0L);
        if (!ready(gameTime, nextApplication)) {
            return false;
        }
        WarlockeryEntityData.get(cloud).putLong(key, nextApplicationTime(gameTime));
        return true;
    }

    static boolean ready(final long gameTime, final long nextApplication) {
        return gameTime >= nextApplication;
    }

    static long nextApplicationTime(final long gameTime) {
        return gameTime + REAPPLICATION_DELAY;
    }

    static boolean blocksDelivery(final CustomBrewDelivery delivery, final boolean gasImmune) {
        return delivery == CustomBrewDelivery.GAS && gasImmune;
    }
}
