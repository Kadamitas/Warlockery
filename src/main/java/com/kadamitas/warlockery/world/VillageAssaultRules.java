package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.transformation.VampireProgressionRules;
import com.kadamitas.warlockery.transformation.WerewolfProgressionRules;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.Difficulty;

public final class VillageAssaultRules {
    public static final int WAVE_COUNT = 3;
    public static final int CHECK_INTERVAL_TICKS = 20;
    public static final int INTERMISSION_TICKS = 200;
    public static final int ASSAULT_DURATION_TICKS = 12_000;
    public static final int SPAWN_RADIUS = 42;
    public static final int MINIMUM_DELAY_TICKS = 24_000;
    public static final int MAXIMUM_DELAY_TICKS = 72_000;
    public static final float ESCAPE_HEALTH_FRACTION = 0.25F;
    public static final int ESCAPE_LIFETIME_TICKS = 240;

    private VillageAssaultRules() {
    }

    public static int waveSize(final AssaultKind kind, final int wave) {
        Objects.requireNonNull(kind, "kind");
        if (wave < 1 || wave > WAVE_COUNT) {
            throw new IllegalArgumentException("Village assault wave must be between 1 and " + WAVE_COUNT);
        }
        return switch (kind) {
            case GOBLIN -> 1 + wave * 2;
            case VAMPIRE -> wave == 1 ? 2 : wave + 2;
            case WEREWOLF -> wave * 2;
        };
    }

    public static long nextDelay(final long roll) {
        final long range = MAXIMUM_DELAY_TICKS - MINIMUM_DELAY_TICKS + 1L;
        return MINIMUM_DELAY_TICKS + Math.floorMod(roll, range);
    }

    public static long nextDelay(final long roll, final double frequencyMultiplier) {
        if (!Double.isFinite(frequencyMultiplier) || frequencyMultiplier <= 0.0) {
            throw new IllegalArgumentException("Village assault frequency multiplier must be positive and finite");
        }
        return Math.max(1L, Math.round(nextDelay(roll) * frequencyMultiplier));
    }

    public static boolean canStart(
        final Difficulty difficulty,
        final SettlementKind settlement,
        final boolean assaultActive,
        final long gameTime,
        final long nextAttempt,
        final boolean night,
        final boolean fullMoon
    ) {
        return difficulty != Difficulty.PEACEFUL
            && settlement != null
            && !assaultActive
            && gameTime >= nextAttempt
            && !eligibleKinds(settlement, night, fullMoon).isEmpty();
    }

    public static boolean allowedAt(
        final AssaultKind kind,
        final SettlementKind settlement,
        final boolean night,
        final boolean fullMoon
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(settlement, "settlement");
        return switch (kind) {
            case GOBLIN -> settlement == SettlementKind.HUMAN;
            case VAMPIRE -> night;
            case WEREWOLF -> night && fullMoon;
        };
    }

    public static List<AssaultKind> eligibleKinds(
        final SettlementKind settlement,
        final boolean night,
        final boolean fullMoon
    ) {
        if (settlement == null) {
            return List.of();
        }
        return List.of(AssaultKind.values()).stream()
            .filter(kind -> allowedAt(kind, settlement, night, fullMoon))
            .toList();
    }

    public static int powerLevel(final AssaultKind kind, final int wave) {
        Objects.requireNonNull(kind, "kind");
        if (wave < 1 || wave > WAVE_COUNT) {
            throw new IllegalArgumentException("Village assault wave must be between 1 and " + WAVE_COUNT);
        }
        return kind == AssaultKind.GOBLIN ? 0 : switch (wave) {
            case 1 -> 3;
            case 2 -> 6;
            case 3 -> 10;
            default -> throw new IllegalStateException("Validated assault wave was outside its range");
        };
    }

    public static int objectiveQuota(final AssaultKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case GOBLIN -> 0;
            case VAMPIRE -> 4;
            case WEREWOLF -> 3;
        };
    }

    public static boolean objectiveSatisfied(
        final AssaultKind kind,
        final int objectiveProgress,
        final int objectiveQuota
    ) {
        Objects.requireNonNull(kind, "kind");
        return kind != AssaultKind.GOBLIN
            && objectiveQuota > 0
            && objectiveProgress >= objectiveQuota;
    }

    public static float nonlethalFeedingDamage(final float victimHealth, final float proposedDamage) {
        if (victimHealth <= 0.0F || proposedDamage <= 0.0F) {
            return 0.0F;
        }
        return Math.min(proposedDamage, Math.max(0.0F, victimHealth - 1.0F));
    }

    public static boolean tradeLocked(final long gameTime, final long lockExpiresAt) {
        return lockExpiresAt > 0L && gameTime < lockExpiresAt;
    }

    public static boolean isFreshObjectiveTarget(
        final AssaultKind kind,
        final String victimId,
        final Set<String> completedVictims,
        final boolean bloodDrained,
        final boolean infected
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(victimId, "victimId");
        Objects.requireNonNull(completedVictims, "completedVictims");
        if (kind == AssaultKind.GOBLIN) {
            return true;
        }
        if (completedVictims.contains(victimId)) {
            return false;
        }
        return kind == AssaultKind.VAMPIRE ? !bloodDrained : !infected;
    }

    public static AbilityImplementation vampireAbilityImplementation(
        final VampireProgressionRules.Ability ability
    ) {
        Objects.requireNonNull(ability, "ability");
        return switch (ability) {
            case BLOOD_SUSTENANCE, SENSE_BLOOD, RESIST_SUN, ZOMBIE_RESPECT ->
                AbilityImplementation.INHERENT;
            case DRINK_BLOOD, POISON_AND_DISEASE_IMMUNITY, SUPERNATURAL_RESILIENCE,
                 TRANSFIX, NIGHT_VISION, KNOCKBACK, SPEED, BATSWARM_FORM, MESMERIZE,
                 TELEPORT, BAT_SWARM -> AbilityImplementation.ACTIVE;
            case SMASH_STONE, CREATE_VAMPIRES, CALL_STORM -> AbilityImplementation.SAFETY_OMITTED;
        };
    }

    public static AbilityImplementation werewolfAbilityImplementation(
        final WerewolfProgressionRules.Ability ability
    ) {
        Objects.requireNonNull(ability, "ability");
        return switch (ability) {
            case FULL_MOON_WOLF_FORM, CONTROLLED_WOLF_FORM, WOLFMAN_FORM ->
                AbilityImplementation.INHERENT;
            case POISON_AND_DISEASE_IMMUNITY, SUPERNATURAL_RESILIENCE, FEAST,
                 CHARGE_ATTACK, STUN_HOWL, PACK_HOWL, ARMOR_RENDING, SPREAD_CURSE ->
                AbilityImplementation.ACTIVE;
            case MUTTON_HARVEST, INSTANT_EARTH_DIGGING, BONE_FINDING ->
                AbilityImplementation.SAFETY_OMITTED;
        };
    }

    public static NpcPowerProfile npcPowers(final AssaultKind kind, final int wave) {
        final int level = powerLevel(kind, wave);
        return switch (kind) {
            case GOBLIN -> new NpcPowerProfile(0, Set.of(), Set.of());
            case VAMPIRE -> new NpcPowerProfile(
                level,
                VampireProgressionRules.abilitiesAt(level),
                Set.of()
            );
            case WEREWOLF -> new NpcPowerProfile(
                level,
                Set.of(),
                WerewolfProgressionRules.abilitiesAt(level)
            );
        };
    }

    public static boolean shouldEscape(
        final AssaultKind kind,
        final float health,
        final float maximumHealth,
        final boolean alreadyEscaped
    ) {
        return (kind == AssaultKind.VAMPIRE || kind == AssaultKind.WEREWOLF)
            && !alreadyEscaped
            && maximumHealth > 0.0F
            && health > 0.0F
            && health <= maximumHealth * ESCAPE_HEALTH_FRACTION;
    }

    public static boolean canInfectVillager(
        final AssaultKind kind,
        final boolean targetIsVanillaVillager,
        final boolean targetIsPlayer
    ) {
        return kind == AssaultKind.WEREWOLF && targetIsVanillaVillager && !targetIsPlayer;
    }

    public static boolean shouldTransformInfected(final boolean night, final boolean fullMoon) {
        return night && fullMoon;
    }

    public static boolean shouldRestoreVillager(final boolean daylight) {
        return daylight;
    }

    public static DefenseReward reward(
        final AssaultKind kind,
        final SettlementKind settlement,
        final int wavesCleared
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(settlement, "settlement");
        final int completed = Math.clamp(wavesCleared, 0, WAVE_COUNT);
        return new DefenseReward(
            completed == WAVE_COUNT,
            2_400 + completed * 1_200,
            600 + completed * 200,
            1_200 + completed * 400,
            completed >= WAVE_COUNT ? 1 : 0,
            switch (kind) {
                case GOBLIN -> RewardTheme.INDUSTRY;
                case VAMPIRE -> RewardTheme.DAWNWARD;
                case WEREWOLF -> RewardTheme.MOONWARD;
            },
            settlement
        );
    }

    public enum AssaultKind {
        GOBLIN,
        VAMPIRE,
        WEREWOLF;

        public String serializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static AssaultKind fromSerializedName(final String name) {
            return java.util.Arrays.stream(values())
                .filter(value -> value.serializedName().equals(name))
                .findFirst()
                .orElse(GOBLIN);
        }
    }

    public enum SettlementKind {
        HUMAN,
        HOBGOBLIN;

        public String serializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static SettlementKind fromSerializedName(final String name) {
            return java.util.Arrays.stream(values())
                .filter(value -> value.serializedName().equals(name))
                .findFirst()
                .orElse(HUMAN);
        }
    }

    public enum RewardTheme {
        INDUSTRY,
        DAWNWARD,
        MOONWARD
    }

    public enum AbilityImplementation {
        ACTIVE,
        INHERENT,
        SAFETY_OMITTED
    }

    public record NpcPowerProfile(
        int progressionLevel,
        Set<VampireProgressionRules.Ability> vampireAbilities,
        Set<WerewolfProgressionRules.Ability> werewolfAbilities
    ) {
        public NpcPowerProfile {
            if (progressionLevel < 0 || progressionLevel > 10) {
                throw new IllegalArgumentException("NPC supernatural power level must be between zero and ten");
            }
            vampireAbilities = Set.copyOf(vampireAbilities);
            werewolfAbilities = Set.copyOf(werewolfAbilities);
        }
    }

    public record DefenseReward(
        boolean complete,
        int villageFavorTicks,
        int absorptionTicks,
        int signatureBoonTicks,
        int signatureAmplifier,
        RewardTheme theme,
        SettlementKind settlement
    ) {
        public DefenseReward {
            if (villageFavorTicks < 0 || absorptionTicks < 0 || signatureBoonTicks < 0
                || signatureAmplifier < 0) {
                throw new IllegalArgumentException("Defense reward durations and amplifiers cannot be negative");
            }
            Objects.requireNonNull(theme, "theme");
            Objects.requireNonNull(settlement, "settlement");
        }
    }
}
