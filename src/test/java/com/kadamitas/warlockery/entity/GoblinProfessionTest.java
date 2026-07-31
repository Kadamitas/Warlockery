package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GoblinProfessionTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void customProfessionsUseMatchingNonNitwitEngineProfessions() {
        assertEquals(4, HobgoblinEntity.GoblinProfession.values().length);
        assertFalse(Arrays.stream(HobgoblinEntity.GoblinProfession.values())
            .map(HobgoblinEntity.GoblinProfession::engineProfession)
            .anyMatch(VillagerProfession.NITWIT::equals));
        assertEquals(4L, Arrays.stream(HobgoblinEntity.GoblinProfession.values())
            .map(HobgoblinEntity.GoblinProfession::engineProfession)
            .distinct()
            .count());
    }
}
