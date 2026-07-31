package com.kadamitas.warlockery.dream;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

public final class SpiritWorldRules {
    public static final double BASE_NIGHTMARE_CHANCE = 0.95;
    public static final double NIGHTMARE_WEAVER_REDUCTION = 0.50;
    public static final double FLOWING_SPIRIT_REDUCTION = 0.10;
    public static final double WISPY_COTTON_REDUCTION = 0.10;
    public static final double DEMON_HEART_INCREASE = 0.35;
    public static final double FIRE_INCREASE = 0.10;

    private SpiritWorldRules() {
    }

    public static EntryDiagnostic diagnoseEntry(
        final boolean serverSide,
        final boolean alreadyDreaming,
        final boolean sourceIsSpiritWorld,
        final boolean destinationAvailable
    ) {
        if (!serverSide) {
            return EntryDiagnostic.CLIENT_SIDE;
        }
        if (alreadyDreaming || sourceIsSpiritWorld) {
            return EntryDiagnostic.ALREADY_DREAMING;
        }
        return destinationAvailable ? EntryDiagnostic.READY : EntryDiagnostic.DESTINATION_UNAVAILABLE;
    }

    public static double nightmareChance(
        final boolean forced,
        final NightmareEnvironment environment
    ) {
        if (forced) {
            return 1.0;
        }
        if (!environment.nightmareWeaver()) {
            return BASE_NIGHTMARE_CHANCE;
        }
        final double adjusted = BASE_NIGHTMARE_CHANCE
            - NIGHTMARE_WEAVER_REDUCTION
            - bounded(environment.flowingSpirit(), 3) * FLOWING_SPIRIT_REDUCTION
            - bounded(environment.wispyCotton(), 2) * WISPY_COTTON_REDUCTION
            + bounded(environment.demonHeart(), 2) * DEMON_HEART_INCREASE
            + bounded(environment.fire(), 3) * FIRE_INCREASE;
        return Math.clamp(adjusted, 0.0, BASE_NIGHTMARE_CHANCE);
    }

    public static boolean entersNightmare(final double chance, final double roll) {
        return Math.clamp(roll, 0.0, 1.0) < Math.clamp(chance, 0.0, 1.0);
    }

    public static boolean fatalDreamDamage(final float health, final float finalDamage) {
        return finalDamage > 0.0F && finalDamage >= health;
    }

    public static long dreamClockTime(final boolean anyNightmare) {
        return anyNightmare ? 18_000L : 6_000L;
    }

    public static boolean naturalSourceScheduled(final long gameTime, final int offset, final int interval) {
        return interval > 0 && Math.floorMod(gameTime + offset, interval) == 0L;
    }

    public static boolean belowNaturalSourceCap(final int present, final int cap) {
        return cap > 0 && present >= 0 && present < cap;
    }

    public static boolean excludesFromSpiritWorld(final Identifier entityType) {
        return "minecraft".equals(entityType.getNamespace()) && "enderman".equals(entityType.getPath());
    }

    public static boolean demonicNightmareEligible(
        final boolean sleepingBrew,
        final boolean nightmareWeaver,
        final boolean flowingSpirit,
        final boolean demonHeart
    ) {
        return sleepingBrew && nightmareWeaver && flowingSpirit && demonHeart;
    }

    public static double demonicNightmareChance(final boolean eligible) {
        return eligible ? 0.08 : 0.0;
    }

    public static List<ItemStackWithSlot> exports(
        final List<ItemStackWithSlot> dreamInventory,
        final Predicate<ItemStack> permitted
    ) {
        return dreamInventory.stream()
            .filter(entry -> !entry.stack().isEmpty())
            .filter(entry -> permitted.test(entry.stack()))
            .map(entry -> new ItemStackWithSlot(entry.slot(), entry.stack().copy()))
            .toList();
    }

    private static int bounded(final int count, final int maximum) {
        return Math.clamp(count, 0, maximum);
    }

    public record NightmareEnvironment(
        boolean nightmareWeaver,
        int flowingSpirit,
        int wispyCotton,
        int demonHeart,
        int fire
    ) {
    }

    public enum EntryDiagnostic {
        READY("ready"),
        CLIENT_SIDE("client_side"),
        ALREADY_DREAMING("already_dreaming"),
        DESTINATION_UNAVAILABLE("destination_unavailable");

        private final String id;

        EntryDiagnostic(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public boolean ready() {
            return this == READY;
        }
    }

    public enum WakeCause {
        ICY_NEEDLE("icy_needle"),
        FATAL_DAMAGE("fatal_damage"),
        BODY_DESTROYED("body_destroyed"),
        RETURN_PORTAL("return_portal"),
        SESSION_RECOVERY("session_recovery");

        private final String id;

        WakeCause(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
