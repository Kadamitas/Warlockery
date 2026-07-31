package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

public final class GoblinHostilityRules {
    private GoblinHostilityRules() {
    }

    public static boolean raidsVillagers(final CreatureKind kind) {
        return kind == CreatureKind.GOBLIN;
    }

    public static boolean canTarget(final CreatureKind kind, final EntityType<?> targetType) {
        return raidsVillagers(kind) && targetType == EntityTypes.VILLAGER;
    }

    public static boolean isHumanVillager(final EntityType<?> entityType) {
        return entityType == EntityTypes.VILLAGER;
    }
}
