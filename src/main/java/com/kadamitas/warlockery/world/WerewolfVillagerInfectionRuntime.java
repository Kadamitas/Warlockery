package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.data.WarlockeryEntityData;

import com.kadamitas.warlockery.entity.WerewolfEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public final class WerewolfVillagerInfectionRuntime {
    public static final String INFECTION_MARKER = "WarlockeryWerewolfInfection";
    public static final String TRANSFORMED_VILLAGER_MARKER = "WarlockeryTransformedVillager";
    private static final String ORIGINAL_PROFESSION = "WarlockeryOriginalVillagerProfession";
    private static final String ORIGINAL_LEVEL = "WarlockeryOriginalVillagerLevel";
    private static final String ORIGINAL_XP = "WarlockeryOriginalVillagerXp";
    private static final String ORIGINAL_BABY = "WarlockeryOriginalVillagerBaby";
    private static final String ORIGINAL_VILLAGER_DATA = "WarlockeryOriginalVillagerData";
    private static final int UPDATE_INTERVAL_TICKS = 40;

    private WerewolfVillagerInfectionRuntime() {
    }

    public static boolean markInfected(final Villager villager) {
        if (villager.getType() != EntityTypes.VILLAGER || isInfected(villager)) {
            return false;
        }
        WarlockeryEntityData.get(villager).putBoolean(INFECTION_MARKER, true);
        return true;
    }

    public static boolean isInfected(final Entity entity) {
        return WarlockeryEntityData.get(entity).getBooleanOr(INFECTION_MARKER, false);
    }

    public static boolean isTransformedVillager(final Entity entity) {
        return WarlockeryEntityData.get(entity).getBooleanOr(TRANSFORMED_VILLAGER_MARKER, false);
    }

    public static void tick(final ServerLevel level) {
        if (level.getGameTime() % UPDATE_INTERVAL_TICKS != 0L) {
            return;
        }
        final boolean night = isNight(level);
        final boolean fullMoon = isFullMoon(level);
        if (VillageAssaultRules.shouldTransformInfected(night, fullMoon)) {
            infectedVillagers(level).forEach(villager -> convertInfectedVillager(level, villager));
            return;
        }
        if (VillageAssaultRules.shouldRestoreVillager(!night)) {
            transformedVillagers(level).forEach(werewolf -> restoreVillager(level, werewolf));
        }
    }

    static Optional<WerewolfEntity> convertInfectedVillager(
        final ServerLevel level,
        final Villager villager
    ) {
        if (villager.getType() != EntityTypes.VILLAGER || !isInfected(villager) || !villager.isAlive()) {
            return Optional.empty();
        }
        final WerewolfEntity werewolf = ModEntities.WEREWOLF.get().spawn(
            level,
            villager.blockPosition(),
            EntitySpawnReason.CONVERSION
        );
        if (werewolf == null) {
            return Optional.empty();
        }
        werewolf.snapTo(
            villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot()
        );
        werewolf.setPersistenceRequired();
        werewolf.setCustomName(villager.getCustomName());
        werewolf.setCustomNameVisible(villager.isCustomNameVisible());
        WarlockeryEntityData.get(werewolf).putBoolean(TRANSFORMED_VILLAGER_MARKER, true);
        WarlockeryEntityData.get(werewolf).putBoolean(ORIGINAL_BABY, villager.isBaby());
        WarlockeryEntityData.get(werewolf).putInt(ORIGINAL_LEVEL, villager.getVillagerData().level());
        WarlockeryEntityData.get(werewolf).putInt(ORIGINAL_XP, villager.getVillagerXp());
        WarlockeryEntityData.get(werewolf).put(ORIGINAL_VILLAGER_DATA, snapshot(villager));
        final long bloodDrainedUntil = VillageAssaultRuntime.bloodDrainedUntil(villager);
        if (bloodDrainedUntil > 0L) {
            WarlockeryEntityData.get(werewolf).putLong(
                VillageAssaultRuntime.BLOOD_DRAINED_UNTIL,
                bloodDrainedUntil
            );
        }
        villager.getVillagerData().profession().unwrapKey().ifPresent(key ->
            WarlockeryEntityData.get(werewolf).putString(ORIGINAL_PROFESSION, key.identifier().toString())
        );
        villager.discard();
        return Optional.of(werewolf);
    }

    static Optional<Villager> restoreVillager(
        final ServerLevel level,
        final WerewolfEntity werewolf
    ) {
        if (!werewolf.isAlive() || !isTransformedVillager(werewolf)) {
            return Optional.empty();
        }
        final Optional<CompoundTag> saved = WarlockeryEntityData.get(werewolf).getCompound(ORIGINAL_VILLAGER_DATA);
        final Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.CONVERSION);
        if (villager == null) {
            return Optional.empty();
        }
        if (saved.isPresent()) {
            villager.load(TagValueInput.create(
                ProblemReporter.DISCARDING,
                level.registryAccess(),
                saved.orElseThrow().copy()
            ));
        } else {
            restoreLegacyState(level, villager, werewolf);
        }
        villager.snapTo(
            werewolf.getX(), werewolf.getY(), werewolf.getZ(), werewolf.getYRot(), werewolf.getXRot()
        );
        villager.setPersistenceRequired();
        WarlockeryEntityData.get(villager).putBoolean(INFECTION_MARKER, true);
        final long bloodDrainedUntil = WarlockeryEntityData.get(werewolf).getLongOr(
            VillageAssaultRuntime.BLOOD_DRAINED_UNTIL,
            VillageAssaultRuntime.bloodDrainedUntil(villager)
        );
        if (bloodDrainedUntil > 0L) {
            WarlockeryEntityData.get(villager).putLong(
                VillageAssaultRuntime.BLOOD_DRAINED_UNTIL,
                bloodDrainedUntil
            );
        }
        if (!level.addFreshEntity(villager)) {
            return Optional.empty();
        }
        werewolf.discard();
        return Optional.of(villager);
    }

    private static CompoundTag snapshot(final Villager villager) {
        final TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING,
            villager.registryAccess()
        );
        villager.saveWithoutId(output);
        return output.buildResult();
    }

    private static void restoreLegacyState(
        final ServerLevel level,
        final Villager villager,
        final WerewolfEntity werewolf
    ) {
        villager.setCustomName(werewolf.getCustomName());
        villager.setCustomNameVisible(werewolf.isCustomNameVisible());
        villager.setVillagerXp(WarlockeryEntityData.get(werewolf).getIntOr(ORIGINAL_XP, 0));
        if (WarlockeryEntityData.get(werewolf).getBooleanOr(ORIGINAL_BABY, false)) {
            villager.setAge(-24_000);
        }
        restoreProfession(level, villager, werewolf);
    }

    private static void restoreProfession(
        final ServerLevel level,
        final Villager villager,
        final WerewolfEntity werewolf
    ) {
        WarlockeryEntityData.get(werewolf).getString(ORIGINAL_PROFESSION)
            .flatMap(value -> Optional.ofNullable(Identifier.tryParse(value)))
            .ifPresent(identifier -> {
                final ResourceKey<VillagerProfession> key = ResourceKey.create(
                    Registries.VILLAGER_PROFESSION,
                    identifier
                );
                villager.setVillagerData(villager.getVillagerData()
                    .withProfession(level.registryAccess(), key)
                    .withLevel(Math.clamp(
                        WarlockeryEntityData.get(werewolf).getIntOr(ORIGINAL_LEVEL, 1),
                        1,
                        5
                    )));
            });
    }

    private static List<Villager> infectedVillagers(final ServerLevel level) {
        return StreamSupport.stream(level.getAllEntities().spliterator(), false)
            .filter(Villager.class::isInstance)
            .map(Villager.class::cast)
            .filter(WerewolfVillagerInfectionRuntime::isInfected)
            .toList();
    }

    private static List<WerewolfEntity> transformedVillagers(final ServerLevel level) {
        return StreamSupport.stream(level.getAllEntities().spliterator(), false)
            .filter(WerewolfEntity.class::isInstance)
            .map(WerewolfEntity.class::cast)
            .filter(WerewolfVillagerInfectionRuntime::isTransformedVillager)
            .toList();
    }

    private static boolean isNight(final ServerLevel level) {
        final long time = level.getOverworldClockTime() % 24_000L;
        return time >= 13_000L && time <= 23_000L;
    }

    private static boolean isFullMoon(final ServerLevel level) {
        return level.environmentAttributes().getValue(
            EnvironmentAttributes.MOON_PHASE,
            net.minecraft.world.phys.Vec3.atCenterOf(level.getRespawnData().pos())
        ) == MoonPhase.FULL_MOON;
    }
}
