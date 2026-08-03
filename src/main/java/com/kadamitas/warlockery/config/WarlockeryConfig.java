package com.kadamitas.warlockery.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class WarlockeryConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ARM_PILLAGERS;
    private static final ForgeConfigSpec.IntValue PILLAGER_SCAN_INTERVAL;
    private static final ForgeConfigSpec.BooleanValue HOBGOBLIN_ENCLAVES;
    private static final ForgeConfigSpec.IntValue HOBGOBLIN_ENCLAVE_INTERVAL;
    private static final ForgeConfigSpec.DoubleValue HOBGOBLIN_ENCLAVE_CHANCE;
    private static final ForgeConfigSpec.BooleanValue SILVER_HUNTS;
    private static final ForgeConfigSpec.IntValue SILVER_HUNT_INTERVAL;
    private static final ForgeConfigSpec.DoubleValue SILVER_HUNT_CHANCE;
    private static final ForgeConfigSpec.BooleanValue SETTLEMENT_FORTIFICATIONS;
    private static final ForgeConfigSpec.BooleanValue VILLAGE_ASSAULTS;
    private static final ForgeConfigSpec.DoubleValue VILLAGE_ASSAULT_FREQUENCY;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("worldEvents");
        ARM_PILLAGERS = builder
            .comment("Allows nearby pillagers to equip silver weapons when they encounter werewolves.")
            .define("armPillagersAgainstWerewolves", true);
        PILLAGER_SCAN_INTERVAL = builder
            .comment("Ticks between checks for pillagers and werewolves near a player.")
            .defineInRange("pillagerScanIntervalTicks", 200, 20, Integer.MAX_VALUE);
        HOBGOBLIN_ENCLAVES = builder
            .comment("Allows travelling hobgoblins to establish small wilderness huts.")
            .define("enableHobgoblinEnclaves", true);
        HOBGOBLIN_ENCLAVE_INTERVAL = builder
            .comment("Ticks between attempts to found a hobgoblin enclave.")
            .defineInRange("hobgoblinEnclaveAttemptIntervalTicks", 2_400, 20, Integer.MAX_VALUE);
        HOBGOBLIN_ENCLAVE_CHANCE = builder
            .comment("Chance from 0.0 to 1.0 for each eligible hobgoblin enclave attempt.")
            .defineInRange("hobgoblinEnclaveChance", 0.1D, 0.0D, 1.0D);
        SILVER_HUNTS = builder
            .comment("Allows rare full-moon battles between werewolves and silver-equipped hunters.")
            .define("enableSilverHunts", true);
        SILVER_HUNT_INTERVAL = builder
            .comment("Ticks between attempts to begin a full-moon silver hunt.")
            .defineInRange("silverHuntAttemptIntervalTicks", 1_200, 20, Integer.MAX_VALUE);
        SILVER_HUNT_CHANCE = builder
            .comment("Chance from 0.0 to 1.0 for each eligible full-moon silver hunt attempt.")
            .defineInRange("silverHuntChance", 1.0D / 14.0D, 0.0D, 1.0D);
        SETTLEMENT_FORTIFICATIONS = builder
            .comment("Allows villages and hobgoblin settlements to construct walls, gates, patrol stairs, and guards.")
            .define("enableSettlementFortifications", true);
        VILLAGE_ASSAULTS = builder
            .comment("Allows goblin, vampire, and werewolf assaults against defended settlements.")
            .define("enableVillageAssaults", true);
        VILLAGE_ASSAULT_FREQUENCY = builder
            .comment("Scales the randomized delay between settlement assaults. Lower values make assaults more frequent.")
            .defineInRange("villageAssaultDelayMultiplier", 1.0D, 0.25D, 16.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private WarlockeryConfig() {
    }

    public static boolean armPillagers() {
        return ARM_PILLAGERS.get();
    }

    public static int pillagerScanInterval() {
        return PILLAGER_SCAN_INTERVAL.get();
    }

    public static boolean hobgoblinEnclaves() {
        return HOBGOBLIN_ENCLAVES.get();
    }

    public static int hobgoblinEnclaveInterval() {
        return HOBGOBLIN_ENCLAVE_INTERVAL.get();
    }

    public static double hobgoblinEnclaveChance() {
        return HOBGOBLIN_ENCLAVE_CHANCE.get();
    }

    public static boolean silverHunts() {
        return SILVER_HUNTS.get();
    }

    public static int silverHuntInterval() {
        return SILVER_HUNT_INTERVAL.get();
    }

    public static double silverHuntChance() {
        return SILVER_HUNT_CHANCE.get();
    }

    public static boolean settlementFortifications() {
        return SETTLEMENT_FORTIFICATIONS.get();
    }

    public static boolean villageAssaults() {
        return VILLAGE_ASSAULTS.get();
    }

    public static double villageAssaultFrequency() {
        return VILLAGE_ASSAULT_FREQUENCY.get();
    }
}
